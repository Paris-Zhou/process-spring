package com.paris.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.paris.properties.DataSourceProperties;
import com.paris.properties.DataSourceProperty;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.paris.mapper.gateway", sqlSessionTemplateRef = "gatewaySqlSessionTemplate")
@EnableConfigurationProperties(DataSourceProperties.class)
public class GatewayDataSourceConfig {
    @Bean("gatewayDataSource")
    public DataSource getCommonDataSource(DataSourceProperties properties) {
        DataSourceProperty common = properties.getDataSourceMap().get("iot-gateway");
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setInitialSize(properties.getInitialSize());
        dataSource.setMaxActive(properties.getMaxActive());
        dataSource.setMinIdle(properties.getMinIdle());
        dataSource.setMaxWait(properties.getMaxWait());
        dataSource.setKeepAlive(properties.isKeepAlive());
        dataSource.setValidationQuery(properties.getValidationQuery());
        dataSource.setDriverClassName(common.getDriverClassName());
        dataSource.setUrl(common.getUrl());
        dataSource.setUsername(common.getUsername());
        dataSource.setPassword(common.getPassword());
        return dataSource;
    }

    @Bean(name = "gatewayTransactionManager")
    public DataSourceTransactionManager dataSourceTransactionManager(@Qualifier("gatewayDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "gatewaySqlSessionFactory")
    public SqlSessionFactory sqlSessionFactoryBean(@Qualifier("gatewayDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        // 指定数据源
        factoryBean.setDataSource(dataSource);
        // factoryBean.setPlugins(new SqlCostInterceptor());
        // 指定mapper xml路径
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] mapperXml = resolver.getResources("classpath:mapper/gateway/*.xml");
        factoryBean.setMapperLocations(mapperXml);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setLogImpl(StdOutImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    @Bean(name = "gatewaySqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate1(@Qualifier("gatewaySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
