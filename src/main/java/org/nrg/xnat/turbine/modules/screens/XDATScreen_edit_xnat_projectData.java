/*
 * web: org.nrg.xnat.turbine.modules.screens.XDATScreen_edit_xnat_projectData
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.turbine.modules.screens;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.display.DisplayManager;
import org.nrg.xdat.om.ArcProject;
import org.nrg.xdat.security.ElementSecurity;
import org.nrg.xdat.turbine.modules.screens.EditScreenA;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.schema.Wrappers.GenericWrapper.GenericWrapperElement;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.turbine.utils.XNATUtils;
import org.nrg.xnat.velocity.context.PostAddProjectContextPopulator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

/**
 * @author XDAT
 *
 */
public class XDATScreen_edit_xnat_projectData extends EditScreenA {
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(XDATScreen_edit_xnat_projectData.class);
	/* (non-Javadoc)
	 * @see org.nrg.xdat.turbine.modules.screens.EditScreenA#getElementName()
	 */
	
	public String getElementName() {
	    return "xnat:projectData";
	}
	
	public ItemI getEmptyItem(RunData data) throws Exception
	{
	    String s = getElementName();
		ItemI temp =  XFTItem.NewItem(s,TurbineUtils.getUser(data));
		return temp;
	}
	/* (non-Javadoc)
	 * @see org.nrg.xdat.turbine.modules.screens.SecureReport#finalProcessing(org.apache.turbine.util.RunData, org.apache.velocity.context.Context)
	 */
	public void finalProcessing(RunData data, Context context) {

        Hashtable hash = XNATUtils.getInvestigatorsForCreate(getElementName(),data);
        context.put("investigators",hash);
        context.put("arc",ArcSpecManager.GetInstance());
        if (TurbineUtils.HasPassedParameter("destination", data)){
            context.put("destination", TurbineUtils.GetPassedParameter("destination", data));
        }
        try {
            ArrayList<ElementSecurity> root = new ArrayList<ElementSecurity>();
            ArrayList<ElementSecurity> subjectAssessors = new ArrayList<ElementSecurity>();
            ArrayList<ElementSecurity> mrAssessors = new ArrayList<ElementSecurity>();
            ArrayList<ElementSecurity> petAssessors = new ArrayList<ElementSecurity>();
            
        	Collection<ElementSecurity> all =ElementSecurity.GetElementSecurities().values();
        	for (ElementSecurity es: all){
                try {
                    if (es.getAccessible() || (item.getStringProperty("ID")!=null && es.matchesUsageEntry(item.getStringProperty("ID")))){
                        GenericWrapperElement g= es.getSchemaElement().getGenericXFTElement();
                        
                        if(g.instanceOf("xnat:mrAssessorData")){
                            mrAssessors.add(es);
                        }else if(g.instanceOf("xnat:petAssessorData")){
                            petAssessors.add(es);
                        }else if(g.instanceOf("xnat:subjectAssessorData")){
                            subjectAssessors.add(es);
                        }else if (g.instanceOf("xnat:subjectData") || g.instanceOf("xnat:experimentData")){
                            root.add(es);
                        }
                    }
                } catch (Throwable e) {
                    logger.error("",e);
                }
            }
            
            context.put("root", root);
            context.put("subjectAssessors", subjectAssessors);
            context.put("mrAssessors", mrAssessors);
            context.put("petAssessors", petAssessors);
            
            // 
            // getBeansOfType(PostAddProjectContextPopulator.class) is not working. 
            //
            // Collection<PostAddProjectContextPopulator>  contextPopulators = XDAT.getContextService().getBeansOfType(PostAddProjectContextPopulator.class).values();
            // if(null != contextPopulators) {
            //     for (PostAddProjectContextPopulator p : contextPopulators) {
            //         context.put(p.getName(), p.getObject());
            //     }
            // }
             
             // Retrieve a single bean of type PostAddProjectContextPopulator
             PostAddProjectContextPopulator populator = XDAT.getContextService().getBeanSafely(PostAddProjectContextPopulator.class);
             if(null != populator) {
                 context.put(populator.getName(), populator.getObject());
             }
                        
			if (item.getProperty("ID")==null)
			{
			    context.put("page_title","New " + DisplayManager.GetInstance().getSingularDisplayNameForProject().toLowerCase());
			}else{
                ArcProject p = ArcSpecManager.GetInstance().getProjectArc(item.getStringProperty("ID"));
                if (p!=null){
                    context.put("arcP", p);
                }
			    context.put("page_title","Edit " + DisplayManager.GetInstance().getSingularDisplayNameForProject() + " Details");
			}
		} catch (Exception e) {
			logger.error("",e);
		}
        
	}
}
