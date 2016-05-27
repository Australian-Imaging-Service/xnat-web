package org.nrg.xnat.event.listeners.methods;

import com.google.common.collect.ImmutableList;
import org.nrg.xdat.XDAT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class SmtpHandlerMethod extends AbstractNotificationsPreferenceHandlerMethod {
    @Override
    public List<String> getHandledPreferences() {
        return PREFERENCES;
    }

    @Override
    public void handlePreferences(final Map<String, String> values) {
        if (!Collections.disjoint(PREFERENCES, values.keySet())) {
            updateSmtp();
        }
    }

    @Override
    public void handlePreference(final String preference, final String value) {
        if(PREFERENCES.contains(preference)){
            updateSmtp();
        }
    }

    private void updateSmtp(){
		try {
            XDAT.getContextService().getBean(JavaMailSenderImpl.class).setHost(XDAT.getNotificationsPreferences().getHostname());
            XDAT.getContextService().getBean(JavaMailSenderImpl.class).setPort(XDAT.getNotificationsPreferences().getPort());
            XDAT.getContextService().getBean(JavaMailSenderImpl.class).setUsername(XDAT.getNotificationsPreferences().getUsername());
            XDAT.getContextService().getBean(JavaMailSenderImpl.class).setPassword(XDAT.getNotificationsPreferences().getPassword());
            XDAT.getContextService().getBean(JavaMailSenderImpl.class).setProtocol(XDAT.getNotificationsPreferences().getProtocol());

		} catch (Exception e1) {
			_log.error("", e1);
		}
	}

    private static final Logger       _log        = LoggerFactory.getLogger(SmtpHandlerMethod.class);
    private static final List<String> PREFERENCES = ImmutableList.copyOf(Arrays.asList("smtp.enabled", "host","port", "username","password", "protocol"));

    @Autowired
    @Lazy
    private JdbcTemplate _template;

}