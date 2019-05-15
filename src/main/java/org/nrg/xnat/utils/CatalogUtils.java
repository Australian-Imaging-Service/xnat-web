/*
 * web: org.nrg.xnat.utils.CatalogUtils
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.utils;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.twmacinta.util.MD5;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.config.entities.Configuration;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.framework.constants.Scope;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.bean.*;
import org.nrg.xdat.bean.base.BaseElement;
import org.nrg.xdat.bean.reader.XDATXMLReader;
import org.nrg.xdat.model.*;
import org.nrg.xdat.om.*;
import org.nrg.xft.XFTTable;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.FileUtils;
import org.nrg.xft.utils.zip.TarUtils;
import org.nrg.xft.utils.zip.ZipI;
import org.nrg.xft.utils.zip.ZipUtils;
import org.nrg.xnat.helpers.resource.XnatResourceInfo;
import org.nrg.xnat.presentation.ChangeSummaryBuilderA;
import org.nrg.xnat.restlet.util.FileWriterWrapperI;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.io.FileUtils.listFiles;

/**
 * @author timo
 */
@SuppressWarnings({"deprecation", "UnusedReturnValue", "Duplicates"})
@Slf4j
public class CatalogUtils {
    public final static String[] FILE_HEADERS        = {"Name", "Size", "URI", "collection", "file_tags", "file_format", "file_content", "cat_ID", "digest"};
    public final static String[] FILE_HEADERS_W_FILE = {"Name", "Size", "URI", "collection", "file_tags", "file_format", "file_content", "cat_ID", "file", "digest"};

    public static final String PROJECT_PATH  = "projectPath";
    public static final String ABSOLUTE_PATH = "absolutePath";
    public static final String LOCATOR       = "locator";
    public static final String URI           = "URI";

    public static boolean getChecksumConfiguration(final XnatProjectdata project) throws ConfigServiceException {
        final String projectId = project.getId();
        final Configuration configuration = XDAT.getConfigService().getConfig("checksums", "checksums", StringUtils.isBlank(projectId) ? Scope.Site : Scope.Project, projectId);

        if (configuration != null) {
            final String checksumProperty = XDAT.getSiteConfigurationProperty("checksums");
            if (!StringUtils.isBlank(checksumProperty)) {
                return Boolean.parseBoolean(checksumProperty);
            }
        }

        return getChecksumConfiguration();
    }

    public static Boolean getChecksumConfiguration() throws ConfigServiceException {
        if (_checksumConfig == null) {
            final String checksumProperty = XDAT.getSiteConfigurationProperty("checksums");
            if (!StringUtils.isBlank(checksumProperty)) {
                _checksumConfig = new AtomicBoolean(Boolean.parseBoolean(checksumProperty));
            }
        }
        return _checksumConfig.get();
    }

    /**
     * This sets the cached value for the checksum configuration. Note that this does <i>not</i> set the persisted
     * configuration value for the checksum configuration. This is used by the {@link org.nrg.xnat.utils.ChecksumsSiteConfigurationListener}
     * to update the cached value whenever the database value is changed elsewhere.
     *
     * @param checksumConfig The value to set for the cached checksum configuration setting.
     * @return The previous value for the cached checksum configuration setting.
     */
    public static Boolean setChecksumConfiguration(boolean checksumConfig) {
        return _checksumConfig.getAndSet(checksumConfig);
    }

    public static void calculateResourceChecksums(final CatCatalogI cat, final File f) {
        for (CatEntryI entry : cat.getEntries_entry()) {
            CatalogUtils.setChecksum(entry, f.getParent());
        }
    }

    public static CatEntryI getEntryByURIOrId(final CatCatalogBean catalogBean, final String filePath) {
        final CatEntryI entry = CatalogUtils.getEntryByURI(catalogBean, filePath);
        if (entry != null) {
            return entry;
        }
        return CatalogUtils.getEntryById(catalogBean, filePath);
    }

    /**
     * Set digest field on entry with corresponding MD5
     *
     * @param entry CatEntryI for operation
     * @param path  Path to catalog (used for relative paths)
     * @return true if entry was modified, false if not.
     */
    private static boolean setChecksum(final CatEntryI entry, final String path) {
        if (StringUtils.isBlank(entry.getDigest())) {//this should only occur if the MD5 isn't already there.
            final File file = CatalogUtils.getFile(entry, path);//this will allow absolute paths to be functional.  Catalogs are sometimes generated by client tools. They may not stay relative to the catalog, as XNAT would make them.
            if (file != null && file.exists()) {//fail safe to missing files, maybe the files haven't been put in place yet...
                final String checksum = getHash(file);
                if (StringUtils.isNotBlank(checksum)) {
                    entry.setDigest(checksum);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculates a checksum hash for the submitted file based on the currently configured hash algorithm. Note that
     * currently XNAT only supports MD5 hashes. If an error that occurs while calculating the checksum, the error is
     * logged and this method returns an empty string.
     *
     * @param file The file for which the checksum should be calculated.
     *
     * @return The checksum for the file if successful, an empty string otherwise.
     */
    @Nonnull
    public static String getHash(final File file) {
        try {
            return MD5.asHex(MD5.getHash(file));
        } catch (IOException e) {
            log.error("An error occurred calculating the checksum for a file at the path: {}", file.getPath(), e);
            return "";
        }
    }

    public static List<Object[]> getEntryDetails(final CatCatalogI cat, final String parentPath, final String uriPath, final XnatResource _resource, final boolean includeFile, final CatEntryFilterI filter, final XnatProjectdata proj, final String locator) {
        final List<Object[]> catalogEntries = new ArrayList<>();
        for (final CatCatalogI subset : cat.getSets_entryset()) {
            catalogEntries.addAll(getEntryDetails(subset, parentPath, uriPath, _resource, includeFile, filter, proj, locator));
        }

        for (final CatEntryI entry : cat.getEntries_entry()) {
            if (filter == null || filter.accept(entry)) {
                final List<Object> row = Lists.newArrayList();
                final String entryPath = StringUtils.replace(FileUtils.AppendRootPath(parentPath, entry.getUri()), "\\", "/");
                final File file = getFileOnLocalFileSystem(entryPath);
                if (file == null) {
                    log.warn("The catalog located at {} contains an invalid entry with the path {}. Please check and/or refresh the catalog.", _resource.getUri(), entryPath);
                    continue;
                }
                row.add(file.getName());
                row.add(includeFile ? 0 : file.length());
                if (locator.equalsIgnoreCase(URI)) {
                    row.add(FileUtils.IsAbsolutePath(entry.getUri()) ? uriPath + "/" + entry.getId() : uriPath + "/" + entry.getUri());
                } else if (locator.equalsIgnoreCase(ABSOLUTE_PATH)) {
                    row.add(entryPath);
                } else if (locator.equalsIgnoreCase(PROJECT_PATH)) {
                    row.add(entryPath.substring(proj.getRootArchivePath().substring(0, proj.getRootArchivePath().lastIndexOf(proj.getId())).length()));
                } else {
                    row.add("");
                }
                row.add(_resource.getLabel());
                final List<String> fieldsAndTags = Lists.newArrayList();
                for (CatEntryMetafieldI meta : entry.getMetafields_metafield()) {
                    fieldsAndTags.add(meta.getName() + "=" + meta.getMetafield());
                }
                for (CatEntryTagI tag : entry.getTags_tag()) {
                    fieldsAndTags.add(tag.getTag());
                }
                row.add(Joiner.on(",").join(fieldsAndTags));
                row.add(entry.getFormat());
                row.add(entry.getContent());
                row.add(_resource.getXnatAbstractresourceId());
                if (includeFile) {
                    row.add(file);
                }
                row.add(entry.getDigest());
                catalogEntries.add(row.toArray());
            }
        }

        return catalogEntries;
    }

    /**
     * Takes a size of a file or heap of memory in the form of a long and returns a formatted readable version in the
     * form of byte units. For example, 46 would become 46B, 1,024 would become 1KB, 1,048,576 would become 1MB, etc.
     *
     * @param size The size in bytes to be formatted.
     * @return A formatted string representing the byte size.
     */
    public static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        return String.format("%.1f %sB", size / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    /**
     * Formats an object's file statistics for display.
     *
     * @param label     The label of the object (session, scan, resource, etc.)
     * @param fileCount The number of files that compose the object.
     * @param rawSize   The size of the files that compose the object.
     * @return A formatted display of the file statistics.
     */
    @SuppressWarnings("unused")
    public static String formatFileStats(final String label, final long fileCount, final Object rawSize) {
        long size = 0;
        if (rawSize != null) {
            if (rawSize instanceof Integer) {
                size = (Integer) rawSize;
            } else if (rawSize instanceof Long) {
                size = (Long) rawSize;
            }
        }
        if (label == null || label.equals("") || label.equalsIgnoreCase("total")) {
            return String.format("%s in %s files", formatSize(size), fileCount);
        }
        return String.format("%s: %s in %s files", label, formatSize(size), fileCount);
    }

    @SuppressWarnings("unused")
    public static Map<File, CatEntryI> getCatalogEntriesForFiles(final String rootPath, final XnatResourcecatalog catalog, final List<File> files) {
        final File catFile = catalog.getCatalogFile(rootPath);
        final String parentPath = catFile.getParent();
        final CatCatalogBean cat = getCatalog(rootPath, catalog);

        final Map<File, CatEntryI> entries = Maps.newHashMap();
        if (cat != null) {
            for (final CatEntryI entry : cat.getEntries_entry()) {
                final String entryPath = StringUtils.replace(FileUtils.AppendRootPath(parentPath, entry.getUri()), "\\", "/");
                final File file = getFileOnLocalFileSystem(entryPath);
                if (file != null && files.contains(file)) {
                    entries.put(file, entry);
                }
            }
        }
        return entries;
    }

    public interface CatEntryFilterI {
        boolean accept(final CatEntryI entry);
    }

    public static CatEntryI getEntryByFilter(final CatCatalogI cat, final CatEntryFilterI filter) {
        CatEntryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getEntryByFilter(subset, filter);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            try {
                if (filter.accept(entry)) {
                    return entry;
                }
            } catch (Exception exception) {
                log.error("Error occurred filtering catalog entry: {}", entry, exception);
            }
        }

        return null;
    }

    public static Collection<CatEntryI> getEntriesByFilter(final CatCatalogI cat, final CatEntryFilterI filter) {
        List<CatEntryI> entries = new ArrayList<>();

        for (CatCatalogI subset : cat.getSets_entryset()) {
            entries.addAll(getEntriesByFilter(subset, filter));
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            try {
                if (filter == null || filter.accept(entry)) {
                    entries.add(entry);

                }
            } catch (Exception exception) {
                log.error("Error occurred filtering catalog entry: {}", entry, exception);
            }
        }

        return entries;
    }

    @SuppressWarnings("unused")
    public static CatCatalogI getCatalogByFilter(final CatCatalogI cat) {
        CatCatalogI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getCatalogByFilter(subset);
            if (e != null) return e;
        }

        return null;
    }

    public static List<File> getFiles(CatCatalogI cat, String parentPath) {
        List<File> al = new ArrayList<>();
        for (CatCatalogI subset : cat.getSets_entryset()) {
            al.addAll(getFiles(subset, parentPath));
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            String entryPath = StringUtils.replace(FileUtils.AppendRootPath(parentPath, entry.getUri()), "\\", "/");
            File f = getFileOnLocalFileSystem(entryPath);

            if (f != null)
                al.add(f);
        }

        return al;
    }

    /**
     * Gets file from file system.  This method supports relative or absolute paths in the CatEntryI. It also supports
     * files that are gzipped on the file system, but don't include .gz in the catalog URI (this used to be very common).
     *
     * @param entry      Catalog Entry for file to be retrieved
     * @param parentPath Path to catalog file directory
     * @return File object represented by CatEntryI
     */
    public static File getFile(CatEntryI entry, String parentPath) {
        String entryPath = StringUtils.replace(FileUtils.AppendRootPath(parentPath, entry.getUri()), "\\", "/");
        return getFileOnLocalFileSystem(entryPath);
    }

    public static Stats getFileStats(CatCatalogI cat, String parentPath) {
        return new Stats(cat, parentPath);
    }

    public static class Stats {
        public int count;
        public long size;

        public Stats(CatCatalogI cat, String parentPath) {
            count = 0;
            size = 0;
            for (final File f : getFiles(cat, parentPath)) {
                if (f != null && f.exists() && !f.getName().endsWith("catalog.xml")) {
                    count++;
                    size += f.length();
                }
            }
        }
    }

    public static Collection<CatEntryI> getEntriesByRegex(final CatCatalogI cat, String regex) {
        List<CatEntryI> entries = new ArrayList<>();
        for (CatCatalogI subset : cat.getSets_entryset()) {
            entries.addAll(getEntriesByRegex(subset, regex));
        }
        for (CatEntryI entry : cat.getEntries_entry()) {
            try {
                if (entry.getUri().matches(regex)) {
                    entries.add(entry);
                }
            } catch (Exception exception) {
                log.error("Error occurred testing catalog entry: {}", entry, exception);
            }
        }
        return entries;
    }

    public static Collection<String> getURIs(CatCatalogI cat) {
        Collection<String> all = Lists.newArrayList();
        for (CatCatalogI subset : cat.getSets_entryset()) {
            all.addAll(getURIs(subset));
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            all.add(entry.getUri());
        }

        return all;
    }

    public static CatEntryI getEntryByURI(CatCatalogI cat, String name) {
        CatEntryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getEntryByURI(subset, name);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            if (entry.getUri().equals(name)) {
                return entry;
            }
        }

        //do the decoded check after the basic match.  URLDecoder is horribly non-performant.
        final String decoded = URLDecoder.decode(name);
        if (decoded != null) {
            for (CatEntryI entry : cat.getEntries_entry()) {
                if ((entry.getUri().equals(decoded))) {
                    return entry;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    public static CatEntryI getEntryByName(CatCatalogI cat, String name) {
        CatEntryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getEntryByName(subset, name);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            String decoded = URLDecoder.decode(name);
            if (entry.getName().equals(name) || entry.getName().equals(decoded)) {
                return entry;
            }
        }

        return null;
    }

    public static CatEntryI getEntryById(CatCatalogI cat, String name) {
        CatEntryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getEntryById(subset, name);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            if (entry.getId().equals(name)) {
                return entry;
            }
        }

        return null;
    }

    public static CatDcmentryI getDCMEntryByUID(CatCatalogI cat, String uid) {
        CatDcmentryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getDCMEntryByUID(subset, uid);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            if (entry instanceof CatDcmentryI && ((CatDcmentryI) entry).getUid().equals(uid)) {
                return (CatDcmentryI) entry;
            }
        }

        return null;
    }

    /**
     * Adds the {@link #RELATIVE_PATH relative path} and {@link #SIZE size} metafields to the submitted {@link CatEntryBean catalog entry bean}.
     *
     * @param entry    The catalog entry bean.
     * @param relative The relative path to the bean's associated resources.
     * @param size     The total size of the bean's associated resources.
     */
    public static void setCatEntryBeanMetafields(final CatEntryBean entry, final String relative, final String size) {
        entry.setCachepath(relative);

        final CatEntryMetafieldBean relativePathMetafield = new CatEntryMetafieldBean();
        relativePathMetafield.setMetafield(relative);
        relativePathMetafield.setName(RELATIVE_PATH);
        entry.addMetafields_metafield(relativePathMetafield);

        final CatEntryMetafieldBean sizeMetafield = new CatEntryMetafieldBean();
        sizeMetafield.setMetafield(size);
        sizeMetafield.setName(SIZE);
        entry.addMetafields_metafield(sizeMetafield);
    }

    @SuppressWarnings("unused")
    public static CatDcmentryI getDCMEntryByInstanceNumber(CatCatalogI cat, Integer num) {
        CatDcmentryI e;
        for (CatCatalogI subset : cat.getSets_entryset()) {
            e = getDCMEntryByInstanceNumber(subset, num);
            if (e != null) return e;
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            if (entry instanceof CatDcmentryI && ((CatDcmentryI) entry).getInstancenumber().equals(num)) {
                return (CatDcmentryI) entry;
            }
        }

        return null;
    }

    public static File getFileOnLocalFileSystem(String fullPath) {
        File f = new File(fullPath);
        if (!f.exists()) {
            if (!fullPath.endsWith(".gz")) {
                f = new File(fullPath + ".gz");
                if (!f.exists()) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return f;
    }

    public static void configureEntry(final CatEntryBean newEntry, final XnatResourceInfo info, boolean modified) {
        if (info.getDescription() != null) {
            newEntry.setDescription(info.getDescription());
        }
        if (info.getFormat() != null) {
            newEntry.setFormat(info.getFormat());
        }
        if (info.getContent() != null) {
            newEntry.setContent(info.getContent());
        }
        if (info.getTags().size() > 0) {
            for (final String entry : info.getTags()) {
                final CatEntryTagBean t = new CatEntryTagBean();
                t.setTag(entry);
                newEntry.addTags_tag(t);
            }
        }
        if (info.getMeta().size() > 0) {
            for (final Map.Entry<String, String> entry : info.getMeta().entrySet()) {
                final CatEntryMetafieldBean meta = new CatEntryMetafieldBean();
                meta.setName(entry.getKey());
                meta.setMetafield(entry.getValue());
                newEntry.addMetafields_metafield(meta);
            }
        }

        if (modified) {
            if (info.getUser() != null && newEntry.getModifiedby() == null) {
                newEntry.setModifiedby(info.getUser().getUsername());
            }
            if (info.getLastModified() != null) {
                newEntry.setModifiedtime(info.getLastModified());
            }
            if (info.getEvent_id() != null && newEntry.getModifiedeventid() == null) {
                newEntry.setModifiedeventid(info.getEvent_id().toString());
            }
        } else {
            if (info.getUser() != null && newEntry.getCreatedby() == null) {
                newEntry.setCreatedby(info.getUser().getUsername());
            }
            if (info.getCreated() != null && newEntry.getCreatedtime() == null) {
                newEntry.setCreatedtime(info.getCreated());
            }
            if (info.getEvent_id() != null && newEntry.getCreatedeventid() == null) {
                newEntry.setCreatedeventid(info.getEvent_id().toString());
            }
        }

    }

    public static void configureEntry(final XnatResource newEntry, final XnatResourceInfo info, final UserI user) throws Exception {
        if (info.getDescription() != null) {
            newEntry.setDescription(info.getDescription());
        }
        if (info.getFormat() != null) {
            newEntry.setFormat(info.getFormat());
        }
        if (info.getContent() != null) {
            newEntry.setContent(info.getContent());
        }
        if (info.getTags().size() > 0) {
            for (final String entry : info.getTags()) {
                final XnatAbstractresourceTag t = new XnatAbstractresourceTag(user);
                t.setTag(entry);
                newEntry.setTags_tag(t);
            }
        }
        if (info.getMeta().size() > 0) {
            for (final Map.Entry<String, String> entry : info.getMeta().entrySet()) {
                final XnatAbstractresourceTag t = new XnatAbstractresourceTag(user);
                t.setTag(entry.getValue());
                t.setName(entry.getKey());
                newEntry.setTags_tag(t);
            }
        }
    }

    public static List<String> storeCatalogEntry(final List<? extends FileWriterWrapperI> fileWriters, final String destination, final XnatResourcecatalog catResource, final XnatProjectdata proj, final boolean extract, final XnatResourceInfo info, final boolean overwrite, final EventMetaI ci) throws Exception {
        final File catFile = catResource.getCatalogFile(proj.getRootArchivePath());
        final String parentPath = catFile.getParent();
        final CatCatalogBean cat = catResource.getCleanCatalog(proj.getRootArchivePath(), false, null, null);

        List<String> duplicates = new ArrayList<>();

        for (FileWriterWrapperI fileWriter : fileWriters) {
            final String filename    = StringUtils.substringAfterLast(StringUtils.substringAfterLast(fileWriter.getName(), "\\"), "/");
            final String compression = FilenameUtils.getExtension(filename);

            if (extract && StringUtils.equalsAnyIgnoreCase(compression, "tar", "gz", "zip", "zar")) {
                log.debug("Found archive file {}", filename);

                ZipI zipper;
                if (compression.equalsIgnoreCase(".tar")) {
                    zipper = new TarUtils();
                } else if (compression.equalsIgnoreCase(".gz")) {
                    zipper = new TarUtils();
                    zipper.setCompressionMethod(ZipOutputStream.DEFLATED);
                } else {
                    zipper = new ZipUtils();
                }

                final File destinationDir = catFile.getParentFile();
                try (final InputStream input = fileWriter.getInputStream()) {
                    @SuppressWarnings("unchecked") final List<File> files = zipper.extract(input, destinationDir.getAbsolutePath(), overwrite, ci);
                    for (final File file : files) {
                        if (!file.isDirectory()) {
                            final String relative = destinationDir.toURI().relativize(file.toURI()).getPath();
                            final CatEntryI entry = getEntryByURI(cat, relative);
                            if (entry == null) {
                                final CatEntryBean newEntry = new CatEntryBean();
                                newEntry.setUri(relative);
                                newEntry.setName(file.getName());

                                configureEntry(newEntry, info, false);

                                cat.addEntries_entry(newEntry);
                            }
                        }
                    }
                }
                if (!overwrite) {
                    duplicates.addAll(zipper.getDuplicates());
                }
            } else {
                final File parentFolder = new File(parentPath);
                final String instance;
                if (!StringUtils.isBlank(fileWriter.getNestedPath())) {
                    instance = makePath(fileWriter.getNestedPath(), filename);
                } else if (StringUtils.isBlank(destination)) {
                    instance = filename;
                } else if (destination.startsWith("/")) {
                    instance = destination.substring(1);
                } else {
                    instance = destination;
                }

                final File saveTo = new File(parentFolder, instance);

                if (saveTo.exists() && !overwrite) {
                    duplicates.add(instance);
                } else {
                    if (saveTo.exists()) {
                        final CatEntryBean entry = (CatEntryBean) getEntryByURI(cat, instance);
                        CatalogUtils.moveToHistory(catFile, saveTo, entry, ci);
                    }

                    if (!saveTo.getParentFile().mkdirs() && !saveTo.getParentFile().exists()) {
                        throw new Exception("Failed to create required directory: " + saveTo.getParentFile().getAbsolutePath());
                    }

                    log.debug("Saving filename {} to file {}", filename, saveTo.getAbsolutePath());

                    fileWriter.write(saveTo);

                    if (saveTo.isDirectory()) {
                        log.debug("Found a directory: {}", saveTo.getAbsolutePath());

                        for (final File movedFile : listFiles(saveTo, null, true)) {
                            final String relativePath = instance + "/" + FileUtils.RelativizePath(saveTo, movedFile).replace('\\', '/');
                            log.debug("Updating catalog entry to: {}", relativePath);
                            updateEntry(cat, relativePath, movedFile, info, ci);
                        }
                    } else {
                        log.debug("Updating catalog entry for file {}", saveTo.getAbsolutePath());
                        updateEntry(cat, instance, saveTo, info, ci);
                    }
                }
            }
        }

        log.debug("Writing catalog file {} with {} total entries", catFile.getAbsolutePath(), cat.getEntries_entry().size());

        writeCatalogToFile(cat, catFile);

        return duplicates;
    }

    private static String makePath(String nestedPath, String name) {
        String separator = nestedPath.contains("\\") ? "\\" : "/";
        StringBuilder path = new StringBuilder(nestedPath);
        if (!nestedPath.endsWith(separator)) {
            path.append(separator);
        }
        return path.append(name).toString();
    }

    public static void refreshAuditSummary(CatCatalogI cat) {
        CatCatalogMetafieldI field = null;
        for (CatCatalogMetafieldI mf : cat.getMetafields_metafield()) {
            if ("AUDIT".equals(mf.getName())) {
                field = mf;
                break;
            }
        }

        if (field == null) {
            field = new CatCatalogMetafieldBean();
            field.setName("AUDIT");
            try {
                cat.addMetafields_metafield(field);
            } catch (Exception ignored) {
            }
        }


        field.setMetafield(convertAuditToString(buildAuditSummary(cat)));
    }

    public static Map<String, Map<String, Integer>> retrieveAuditySummary(CatCatalogI cat) {
        if (cat == null) return new HashMap<>();
        CatCatalogMetafieldI field = null;
        for (CatCatalogMetafieldI mf : cat.getMetafields_metafield()) {
            if ("AUDIT".equals(mf.getName())) {
                field = mf;
                break;
            }
        }

        if (field != null) {
            return convertAuditToMap(field.getMetafield());
        } else {
            return buildAuditSummary(cat);
        }

    }

    public static void addAuditEntry(Map<String, Map<String, Integer>> summary, String key, String action, Integer i) {
        if (!summary.containsKey(key)) {
            summary.put(key, new HashMap<String, Integer>());
        }

        if (!summary.get(key).containsKey(action)) {
            summary.get(key).put(action, 0);
        }

        summary.get(key).put(action, summary.get(key).get(action) + i);
    }

    public static void addAuditEntry(Map<String, Map<String, Integer>> summary, Integer eventId, Object d, String action, Integer i) {
        String key = eventId + ":" + d;
        addAuditEntry(summary, key, action, i);
    }

    public static void writeCatalogToFile(CatCatalogI xml, File dest) throws Exception {
        try {
            writeCatalogToFile(xml, dest, getChecksumConfiguration());
        } catch (ConfigServiceException exception) {
            throw new Exception("Error attempting to retrieve checksum configuration", exception);
        }
    }

    public static void writeCatalogToFile(CatCatalogI xml, File dest, boolean calculateChecksums) throws Exception {
        if (calculateChecksums) {
            CatalogUtils.calculateResourceChecksums(xml, dest);
        }

        if (!dest.getParentFile().exists()) {
            if (!dest.getParentFile().mkdirs() && !dest.getParentFile().exists()) {
                throw new Exception("Failed to create required directory: " + dest.getParentFile().getAbsolutePath());
            }
        }

        refreshAuditSummary(xml);

        try (final FileOutputStream fos = new FileOutputStream(dest)) {
            final FileLock fl = fos.getChannel().lock();
            try {
                final OutputStreamWriter fw = new OutputStreamWriter(fos);
                xml.toXML(fw);
                fw.flush();
            } finally {
                fl.release();
            }
        }
    }

    public static File getCatalogFile(final String rootPath, final XnatResourcecatalogI resource) {
        String fullPath = getFullPath(rootPath, resource);
        if (fullPath.endsWith("\\")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }
        if (fullPath.endsWith("/")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }


        File f = new File(fullPath);
        if (!f.exists()) {
            f = new File(fullPath + ".gz");
        }

        if (!f.exists()) {
            f = new File(fullPath);

            CatCatalogBean cat = new CatCatalogBean();
            if (resource.getLabel() != null) {
                cat.setId(resource.getLabel());
            } else {
                cat.setId("" + Calendar.getInstance().getTimeInMillis());
            }

            try {
                if (!f.getParentFile().mkdirs() && !f.getParentFile().exists()) {
                    throw new Exception("Failed to create required directory: " + f.getParentFile().getAbsolutePath());
                }

                FileWriter fw = new FileWriter(f);
                cat.toXML(fw, true);
                fw.close();
            } catch (IOException exception) {
                log.error("Error writing to the folder: {}", f.getParentFile().getAbsolutePath(), exception);
            } catch (Exception exception) {
                log.error("Error creating the folder: {}", f.getParentFile().getAbsolutePath(), exception);
            }
        }

        return f;
    }

    public static CatCatalogBean getCatalog(File catalogFile) {
        if (!catalogFile.exists()) return null;
        try {
            InputStream fis = new FileInputStream(catalogFile);
            if (catalogFile.getName().endsWith(".gz")) {
                fis = new GZIPInputStream(fis);
            }

            BaseElement base;

            XDATXMLReader reader = new XDATXMLReader();
            base = reader.parse(fis);

            if (base instanceof CatCatalogBean) {
                return (CatCatalogBean) base;
            }
        } catch (FileNotFoundException exception) {
            log.error("Couldn't find file: {}", catalogFile, exception);
        } catch (IOException exception) {
            log.error("Error occurred reading file: {}", catalogFile, exception);
        } catch (SAXException exception) {
            log.error("Error processing XML in file: {}", catalogFile, exception);
        }

        return null;
    }

    /**
     * Parses catalog xml for resource and returns the Bean object.  Returns null if not found.
     *
     * @param rootPath The root path for the catalog.
     * @param resource The resource catalog.
     * @return The initialized catalog bean.
     */
    public static CatCatalogBean getCatalog(String rootPath, XnatResourcecatalogI resource) {
        File catalogFile = null;
        try {
            catalogFile = CatalogUtils.getCatalogFile(rootPath, resource);
            if (catalogFile.getName().endsWith(".gz")) {
                FileUtils.GUnzipFiles(catalogFile);
                catalogFile = CatalogUtils.getCatalogFile(rootPath, resource);
            }
        } catch (FileNotFoundException exception) {
            log.error("Couldn't find file: {}", catalogFile, exception);
        } catch (IOException exception) {
            log.error("Error occurred reading file: {}", catalogFile, exception);
        } catch (Exception exception) {
            log.error("Unknown exception reading file at: {}", rootPath, exception);
        }

        return catalogFile != null ? getCatalog(catalogFile) : null;
    }

    public static CatCatalogBean getCleanCatalog(String rootPath, XnatResourcecatalogI resource, boolean includeFullPaths) {
        return getCleanCatalog(rootPath, resource, includeFullPaths, null, null);
    }

    public static CatCatalogBean getCleanCatalog(String rootPath, XnatResourcecatalogI resource, boolean includeFullPaths, UserI user, EventMetaI c) {
        File catalogFile = null;
        try {
            catalogFile = handleCatalogFile(rootPath, resource);

            InputStream fis = new FileInputStream(catalogFile);
            if (catalogFile.getName().endsWith(".gz")) {
                fis = new GZIPInputStream(fis);
            }

            BaseElement base;

            XDATXMLReader reader = new XDATXMLReader();
            base = reader.parse(fis);

            String parentPath = catalogFile.getParent();

            if (base instanceof CatCatalogBean) {
                CatCatalogBean cat = (CatCatalogBean) base;
                formalizeCatalog(cat, parentPath, user, c);

                if (includeFullPaths) {
                    CatCatalogMetafieldBean mf = new CatCatalogMetafieldBean();
                    mf.setName("CATALOG_LOCATION");
                    mf.setMetafield(parentPath);
                    cat.addMetafields_metafield(mf);
                }

                return cat;
            }
        } catch (FileNotFoundException exception) {
            log.error("Couldn't find file indicated by {}", catalogFile.getAbsolutePath(), exception);
        } catch (SAXException exception) {
            log.error("Couldn't parse file indicated by {}", catalogFile.getAbsolutePath(), exception);
        } catch (IOException exception) {
            log.error("Couldn't parse or unzip file indicated by {}", catalogFile.getAbsolutePath(), exception);
        } catch (Exception exception) {
            log.error("Unknown error handling file {}", catalogFile != null ? "indicated by " + catalogFile.getAbsolutePath() : "of unknown location", exception);
        }

        return null;
    }

    /**
     * Reviews existing catalog and adds any missing fields
     *
     * @param cat     Catalog entry to be cleaned
     * @param catPath Path to catalog file (used to access files with relative paths).
     * @param user    User in operation
     * @param now     Corresponding event
     * @return true if catalog was modified, otherwise false
     */
    public static boolean formalizeCatalog(final CatCatalogI cat, final String catPath, UserI user, EventMetaI now) {
        return formalizeCatalog(cat, catPath, user, now, false, false);
        //default to false for checksums for now.  Maybe it should use the default setting for the server.  But, this runs every time a catalog xml is loaded.  So, it will get re-run over and over.  Not sure we want to add that amount of processing.
    }

    /**
     * Reviews existing catalog and adds any missing fields
     *
     * @param cat                Catalog entry to be cleaned
     * @param catPath            Path to catalog file (used to access files with relative paths).
     * @param user               User in operation
     * @param now                Corresponding event
     * @param createChecksums    Boolean whether or not to generate checksums (if missing)
     * @param removeMissingFiles Boolean whether or not to delete references to missing files
     * @return true if catalog was modified, otherwise false
     */
    public static boolean formalizeCatalog(final CatCatalogI cat, final String catPath, UserI user, EventMetaI now, boolean createChecksums, boolean removeMissingFiles) {
        return formalizeCatalog(cat, catPath, cat.getId(), user, now, createChecksums, removeMissingFiles);
    }

    public static String getFullPath(String rootPath, XnatResourcecatalogI resource) {

        String fullPath = StringUtils.replace(FileUtils.AppendRootPath(rootPath, resource.getUri()), "\\", "/");
        while (fullPath.contains("//")) {
            fullPath = StringUtils.replace(fullPath, "//", "/");
        }

        if (!fullPath.endsWith("/")) {
            fullPath += "/";
        }

        return fullPath;
    }

    @SuppressWarnings("unused")
    public boolean modifyEntry(CatCatalogI cat, CatEntryI oldEntry, CatEntryI newEntry) {
        for (int i = 0; i < cat.getEntries_entry().size(); i++) {
            CatEntryI e = cat.getEntries_entry().get(i);
            if (e.getUri().equals(oldEntry.getUri())) {
                cat.getEntries_entry().remove(i);
                cat.getEntries_entry().add(newEntry);
                return true;
            }
        }

        for (CatCatalogI subset : cat.getSets_entryset()) {
            if (modifyEntry(subset, oldEntry, newEntry)) {
                return true;
            }
        }

        return false;
    }

    public static List<File> findHistoricalCatFiles(File catFile) {
        final List<File> files = new ArrayList<>();

        final File historyDir = FileUtils.BuildHistoryParentFile(catFile);

        final String name = catFile.getName();

        final FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String arg1) {
                return (arg1.equals(name));
            }
        };

        if (historyDir.exists()) {
            final File[] historyFiles = historyDir.listFiles();
            if (historyFiles != null) {
                for (File d : historyFiles) {
                    if (d.isDirectory()) {
                        final File[] matched = d.listFiles(filter);
                        if (matched != null && matched.length > 0) {
                            files.addAll(Arrays.asList(matched));
                        }
                    }
                }
            }
        }

        return files;
    }

    public static boolean removeEntry(CatCatalogI cat, CatEntryI entry) {
        for (int i = 0; i < cat.getEntries_entry().size(); i++) {
            CatEntryI e = cat.getEntries_entry().get(i);
            if (e.getUri().equals(entry.getUri())) {
                cat.getEntries_entry().remove(i);
                return true;
            }
        }

        for (CatCatalogI subset : cat.getSets_entryset()) {
            if (removeEntry(subset, entry)) {
                return true;
            }
        }

        return false;
    }

    public static Boolean maintainFileHistory() {
        if (_maintainFileHistory == null) {
            _maintainFileHistory = new AtomicBoolean(XDAT.getBoolSiteConfigurationProperty("audit.maintain-file-history", false));
        }
        return _maintainFileHistory.get();
    }

    public static void moveToHistory(File catFile, File f, CatEntryBean entry, EventMetaI ci) throws Exception {
        //move existing file to audit trail
        if (CatalogUtils.maintainFileHistory()) {
            final File newFile = FileUtils.MoveToHistory(f, EventUtils.getTimestamp(ci));
            addCatHistoryEntry(catFile, newFile.getAbsolutePath(), entry, ci);
        }
    }

    public static void addCatHistoryEntry(File catFile, String f, CatEntryBean entry, EventMetaI ci) throws Exception {
        //move existing file to audit trail
        CatEntryBean newEntryBean = (CatEntryBean) entry.copy();
        newEntryBean.setUri(f);
        if (ci != null) {
            newEntryBean.setModifiedtime(ci.getEventDate());
            if (ci.getEventId() != null) {
                newEntryBean.setModifiedeventid(ci.getEventId().toString());
            }
            if (ci.getUser() != null) {
                newEntryBean.setModifiedby(ci.getUser().getUsername());
            }
        }

        File newCatFile = FileUtils.BuildHistoryFile(catFile, EventUtils.getTimestamp(ci));
        CatCatalogBean newCat;
        if (newCatFile.exists()) {
            newCat = CatalogUtils.getCatalog(newCatFile);
            if (newCat == null) {
                log.warn("Tried to create a new catalog based on the file {} but it's null. Check the logs for errors that may have caused this issue.", newCatFile);
                return;
            }
        } else {
            newCat = new CatCatalogBean();
        }

        newCat.addEntries_entry(newEntryBean);

        CatalogUtils.writeCatalogToFile(newCat, newCatFile);
    }

    public static XFTTable populateTable(XFTTable table, UserI user, XnatProjectdata proj, boolean cacheFileStats) {
        XFTTable newTable = new XFTTable();
        String[] fields = {"xnat_abstractresource_id", "label", "element_name", "category", "cat_id", "cat_desc", "file_count", "file_size", "tags", "content", "format"};
        newTable.initTable(fields);
        table.resetRowCursor();
        while (table.hasMoreRows()) {
            Object[] old = table.nextRow();
            Object[] _new = new Object[11];
            log.debug("Found resource with ID: {}({})", old[0], old[1]);

            _new[0] = old[0];
            _new[1] = old[1];
            _new[2] = old[2];
            _new[3] = old[3];
            _new[4] = old[4];
            _new[5] = old[5];

            XnatAbstractresource res = XnatAbstractresource.getXnatAbstractresourcesByXnatAbstractresourceId(old[0], user, false);
            if (res == null) {
                log.warn("User {} tried to get an abstract resource for the ID {}, but that was null.", user.getUsername(), old[0]);
                continue;
            }

            if (cacheFileStats) {
                if (res.getFileCount() == null) {
                    res.setFileCount(res.getCount(proj.getRootArchivePath()));
                }
                if (res.getFileSize() == null) {
                    res.setFileSize(res.getSize(proj.getRootArchivePath()));
                }
                try {
                    res.save(user, true, false, null);
                } catch (Exception exception) {
                    if (res instanceof XnatResourcecatalog) {
                        log.error("Failed to save updates to resource catalog: {}", res.getLabel(), exception);
                    } else {
                        log.error("Failed to save updates to abstract resource: {}", res.getXnatAbstractresourceId(), exception);
                    }
                }
            }

            _new[6] = res.getFileCount();
            _new[7] = res.getFileSize();
            _new[8] = res.getTagString();
            _new[9] = res.getContent();
            _new[10] = res.getFormat();

            newTable.rows().add(_new);
        }

        return newTable;
    }

    public static boolean populateStats(XnatAbstractresource abstractResource, String rootPath) {
        Integer c = abstractResource.getCount(rootPath);
        Long s = abstractResource.getSize(rootPath);

        boolean modified = false;

        if (!c.equals(abstractResource.getFileCount())) {
            abstractResource.setFileCount(c);
            modified = true;
        }

        if (!s.equals(abstractResource.getFileSize())) {
            abstractResource.setFileSize(s);
            modified = true;
        }

        return modified;
    }

    private static void updateEntry(CatCatalogBean cat, String dest, File f, XnatResourceInfo info, EventMetaI ci) {
        final CatEntryBean e = (CatEntryBean) getEntryByURI(cat, dest);

        if (e == null) {
            final CatEntryBean newEntry = new CatEntryBean();
            newEntry.setUri(dest);
            newEntry.setName(f.getName());

            configureEntry(newEntry, info, false);

            cat.addEntries_entry(newEntry);
        } else {
            if (ci != null) {
                if (ci.getUser() != null)
                    e.setModifiedby(ci.getUser().getUsername());
                e.setModifiedtime(ci.getEventDate());
                if (ci.getEventId() != null) {
                    e.setModifiedeventid(ci.getEventId().toString());
                }
            }
        }
    }

    private static String convertAuditToString(Map<String, Map<String, Integer>> summary) {
        StringBuilder sb = new StringBuilder();
        int counter1 = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : summary.entrySet()) {
            if (counter1++ > 0) sb.append("|");
            sb.append(entry.getKey()).append("=");
            int counter2 = 0;
            for (Map.Entry<String, Integer> sub : entry.getValue().entrySet()) {
                sb.append(sub.getKey()).append(":").append(sub.getValue());
                if (counter2++ > 0) sb.append(";");
            }

        }
        return sb.toString();
    }

    private static Map<String, Map<String, Integer>> convertAuditToMap(final String audit) {
        Map<String, Map<String, Integer>> summary = new HashMap<>();
        for (final String changeSet : audit.split("\\|")) {
            final String[] split1 = changeSet.split("=");
            if (split1.length > 1) {
                final String key = split1[0];
                final Map<String, Integer> counts = new HashMap<>();
                for (final String operation : split1[1].split(";")) {
                    final String[] entry = operation.split(":");
                    counts.put(entry[0], Integer.valueOf(entry[1]));
                }
                summary.put(key, counts);
            }
        }
        return summary;
    }

    private static Map<String, Map<String, Integer>> buildAuditSummary(CatCatalogI cat) {
        Map<String, Map<String, Integer>> summary = new HashMap<>();
        buildAuditSummary(cat, summary);
        return summary;
    }

    private static void buildAuditSummary(CatCatalogI cat, Map<String, Map<String, Integer>> summary) {
        for (CatCatalogI subSet : cat.getSets_entryset()) {
            buildAuditSummary(subSet, summary);
        }

        for (CatEntryI entry : cat.getEntries_entry()) {
            addAuditEntry(summary, entry.getCreatedeventid(), entry.getCreatedtime(), ChangeSummaryBuilderA.ADDED, 1);

            if (entry.getModifiedtime() != null) {
                addAuditEntry(summary, entry.getModifiedeventid(), entry.getModifiedtime(), ChangeSummaryBuilderA.REMOVED, 1);
            }
        }
    }

    private static File handleCatalogFile(final String rootPath, final XnatResourcecatalogI resource) {
        File catalog = CatalogUtils.getCatalogFile(rootPath, resource);
        if (catalog.getName().endsWith(".gz")) {
            try {
                FileUtils.GUnzipFiles(catalog);
                catalog = CatalogUtils.getCatalogFile(rootPath, resource);
            } catch (FileNotFoundException exception) {
                log.error("Couldn't find file: {}", catalog, exception);
            } catch (IOException exception) {
                log.error("Error occurred reading file: {}", catalog, exception);
            }
        }
        return catalog;
    }

    /**
     * Reviews the catalog directory and adds any files that aren't already referenced in the catalog.
     *
     * @param catFile  path to catalog xml file
     * @param cat      content of catalog xml file
     * @param user     user for transaction
     * @param event_id event id for transaction
     * @return true if the cat was modified (and needs to be saved).
     */
    public static boolean addUnreferencedFiles(final File catFile, final CatCatalogI cat, UserI user, Number event_id) {
        //list of all files in the catalog folder
        final Collection<File> files = listFiles(catFile.getParentFile(), null, true);

        //verify that there is only one catalog xml in this directory
        //fail if more then one is present -- otherwise they will be merged.
        for (final File f : files) {
            if (!f.equals(catFile)) {
                if (f.getName().endsWith(".xml") && isCatalogFile(f)) {
                    return false;
                }
            }
        }

        //URI object for the catalog folder (used to generate relative file paths)
        final URI catFolderURI = catFile.getParentFile().toURI();

        final Date now = Calendar.getInstance().getTime();

        boolean modified = false;

        for (final File f : files) {
            if (!f.equals(catFile)) {//don't add the catalog xml to its own list
                //relative path is used to compare to existing catalog entries, and add it if its missing.  entry paths are relative to the location of the catalog file.
                final String relative = catFolderURI.relativize(f.toURI()).getPath();

                //
                final CatEntryI e = getEntryByURI(cat, relative);

                if (e == null) {
                    final CatEntryBean newEntry = new CatEntryBean();
                    newEntry.setUri(relative);
                    newEntry.setName(f.getName());

                    //create basic resource info to specify file properties at creation.
                    final XnatResourceInfo info = XnatResourceInfo.buildResourceInfo(null, null, null, null, user, now, now, event_id);
                    configureEntry(newEntry, info, false);

                    try {
                        cat.addEntries_entry(newEntry);
                        modified = true;
                    } catch (Exception exception) {
                        //this shouldn't happen
                        log.error("Something very weird occurred when adding catalog entries", exception);
                    }
                }

            }
        }

        return modified;
    }

    private static boolean isCatalogFile(File f) {
        if (f.getName().endsWith("_catalog.xml")) {
            return true;
        }
        try {
            if (org.apache.commons.io.FileUtils.readFileToString(f, Charset.defaultCharset()).contains("<cat:Catalog")) {
                return true;
            }
        } catch (IOException e) {
            // Do nothing for now
        }
        return false;
    }

    /**
     * Reviews the catalog directory and returns any files that aren't already referenced in the catalogs in that folder.
     *
     * @param catFolder path to catalog xml folder
     * @return true if the cat was modified (and needs to be saved).
     */
    public static List<String> getUnreferencedFiles(final File catFolder) {
        final List<String> unreferenced = Lists.newArrayList();

        //list of all files in the catalog folder
        final String[] files = catFolder.list();

        //identify the catalog XMLs in this folder
        final List<CatCatalogI> catalogs = Lists.newArrayList();
        if (files != null) {
            for (final String filename : files) {
                if (filename.endsWith(".xml")) {
                    File f = new File(catFolder, filename);
                    if (isCatalogFile(f)) {
                        CatCatalogI cat = CatalogUtils.getCatalog(f);
                        if (cat != null) {
                            catalogs.add(cat);
                        }
                    }
                }
            }
        }

        Collection<String> cataloged = new TreeSet<>();
        for (CatCatalogI cat : catalogs) {
            cataloged.addAll(getURIs(cat));
        }

        if (files != null) {
            for (final String f : files) {
                if (!(f.endsWith(".xml"))) {//ignore catalog files
                    if (!cataloged.remove(f)) {
                        unreferenced.add(f);
                    }
                }
            }
        }

        return unreferenced;
    }

    private static boolean formalizeCatalog(final CatCatalogI cat, final String catPath, String header, UserI user, EventMetaI now, final boolean createChecksum, final boolean removeMissingFiles) {
        boolean modified = false;

        for (CatCatalogI subSet : cat.getSets_entryset()) {
            if (formalizeCatalog(subSet, catPath, header + "/" + subSet.getId(), user, now, createChecksum, removeMissingFiles)) {
                modified = true;
            }
        }

        List<CatEntryI> toRemove = Lists.newArrayList();

        for (CatEntryI entry : cat.getEntries_entry()) {
            if (entry.getCreatedby() == null && user != null) {
                entry.setCreatedby(user.getUsername());
                modified = true;
            }
            if (entry.getCreatedtime() == null && now != null) {
                ((CatEntryBean) entry).setCreatedtime(now.getEventDate());
                modified = true;
            }
            if (entry.getCreatedeventid() == null && now != null && now.getEventId() != null) {
                ((CatEntryBean) entry).setCreatedeventid(now.getEventId().toString());
                modified = true;
            }

            if (createChecksum) {
                if (CatalogUtils.setChecksum(entry, catPath)) {
                    modified = true;
                }
            }

            if (StringUtils.isEmpty(entry.getId()) || removeMissingFiles) {
                String entryPath = StringUtils.replace(FileUtils.AppendRootPath(catPath, entry.getUri()), "\\", "/");
                File f = getFileOnLocalFileSystem(entryPath);
                if (f != null && StringUtils.isEmpty(entry.getId())) {
                    entry.setId(header + "/" + f.getName());
                    modified = true;
                } else if (f == null) {
                    if (removeMissingFiles) {
                        toRemove.add(entry);
                        modified = true;
                    } else {
                        log.warn("The catalog with ID {} located in the folder {} contains an invalid entry with the path {}. Please check and/or refresh the catalog.", cat.getId(), catPath, entryPath);
                    }
                }
            }

            if (entry.getClass().equals(CatDcmentryBean.class)) { // CatDcmentryBeans fail to set format correctly because it's not in their xml
                entry.setFormat("DICOM");
            }
        }

        if (!toRemove.isEmpty()) {
            for (final CatEntryI entry : toRemove) {
                CatalogUtils.removeEntry(cat, entry);
            }
        }

        return modified;
    }

    private static final String RELATIVE_PATH = "RELATIVE_PATH";
    private static final String SIZE          = "SIZE";

    private static AtomicBoolean _maintainFileHistory = null;
    private static AtomicBoolean _checksumConfig      = null;
}
