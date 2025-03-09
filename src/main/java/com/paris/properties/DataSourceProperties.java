package com.paris.properties;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Data
@ConfigurationProperties(prefix = DataSourceProperties.PREFIX)
public class DataSourceProperties {
    public static final String PREFIX = "customize.datasource";

    public final static String DEFAULT_VALIDATION_QUERY = "select 1;";
    private int initialSize;
    private int maxActive;
    private int minIdle;
    private long maxWait;
    private boolean keepAlive = true;
    private String validationQuery = DEFAULT_VALIDATION_QUERY;


    private Map<String, DataSourceProperty> dataSourceMap = new LinkedHashMap<>();

}
