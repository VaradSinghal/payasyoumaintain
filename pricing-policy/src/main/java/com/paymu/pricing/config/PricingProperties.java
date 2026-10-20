package com.paymu.pricing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for pricing-policy.
 */
@Component
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    private String timezone = "Asia/Kolkata";
    private long profileCacheTtlMinutes = 60;
    // demo data, replaced by a real prior-policy lookup
    private Map<String, Double> demoNcb = new HashMap<>();

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public long getProfileCacheTtlMinutes() {
        return profileCacheTtlMinutes;
    }

    public void setProfileCacheTtlMinutes(long profileCacheTtlMinutes) {
        this.profileCacheTtlMinutes = profileCacheTtlMinutes;
    }

    public Map<String, Double> getDemoNcb() {
        return demoNcb;
    }

    public void setDemoNcb(Map<String, Double> demoNcb) {
        this.demoNcb = demoNcb;
    }
}
