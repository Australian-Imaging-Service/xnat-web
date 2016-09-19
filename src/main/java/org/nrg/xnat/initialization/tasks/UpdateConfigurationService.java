package org.nrg.xnat.initialization.tasks;

import org.hibernate.SessionFactory;
import org.nrg.config.entities.Configuration;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xnat.utils.XnatUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
public class UpdateConfigurationService extends AbstractInitializingTask {
    @Autowired
    public UpdateConfigurationService(final JdbcTemplate template, final SessionFactory sessionFactory, final XnatUserProvider primaryAdminUserProvider) {
        super();
        _template = template;
        _sessionFactory = sessionFactory;
        _userProvider = primaryAdminUserProvider;
    }

    @Override
    public String getTaskName() {
        return "Update configuration service to convert long IDs to project IDs.";
    }

    @Override
    protected void callImpl() throws InitializingTaskException {
        final List<Long> projectIds = _template.query(QUERY, new RowMapper<Long>() {
            @Override
            public Long mapRow(final ResultSet resultSet, final int index) throws SQLException {
                return resultSet.getLong(1);
            }
        });
        if (projectIds.size() == 0) {
            _log.info("No suspect configuration entries found.");
        } else {
            for (final Long projectId : projectIds) {
                final List<XnatProjectdata> projects = XnatProjectdata.getXnatProjectdatasByField("xnat:projectData/projectdata_info", projectId, _userProvider.get(), false);
                if (projects.size() == 0) {
                    _log.warn("Processed configurations with project set to {} metadata ID. Can't find a corresponding project.", projectId);
                } else {
                    final XnatProjectdata project = projects.get(0);
                    final String targetId = project.getId();
                    _log.warn("Updating configuration with project set to {} metadata ID to use project ID {} as entity ID.", projectId, targetId);
                    _template.update(UPDATE, targetId, projectId);
                }
            }
            _sessionFactory.getCache().evictEntityRegion(Configuration.class);
        }
    }

    private static final Logger _log   = LoggerFactory.getLogger(UpdateConfigurationService.class);
    private static final String QUERY  = "SELECT DISTINCT project FROM xhbm_configuration WHERE entity_id IS null AND project IS NOT NULL";
    private static final String UPDATE = "UPDATE xhbm_configuration SET entity_id = ?, SET project = DEFAULT WHERE project = ?";

    private final JdbcTemplate     _template;
    private final SessionFactory   _sessionFactory;
    private final XnatUserProvider _userProvider;
}