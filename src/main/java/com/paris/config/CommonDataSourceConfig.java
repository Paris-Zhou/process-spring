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

/**
 * @author : youyihe
 * @date: 2023/04/27 13:39
 * @description: <描述>
 */
@Configuration
@MapperScan(basePackages = "com.paris.mapper.common", sqlSessionTemplateRef = "commonSqlSessionTemplate")
@EnableConfigurationProperties(DataSourceProperties.class)
public class CommonDataSourceConfig {


    @Bean("commonDataSource")
    public DataSource getCommonDataSource(DataSourceProperties properties) {
        DataSourceProperty common = properties.getDataSourceMap().get("nader-common");
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

    @Bean(name = "commonTransactionManager")
    public DataSourceTransactionManager dataSourceTransactionManager(@Qualifier("commonDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "commonSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactoryBean(@Qualifier("commonDataSource") DataSource dataSource) throws Exception {

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();

        // 指定数据源
        factoryBean.setDataSource(dataSource);
        // factoryBean.setPlugins(new SqlCostInterceptor());
        // 指定mapper xml路径
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] mapperXml = resolver.getResources("classpath:mapper/common/*.xml");
        factoryBean.setMapperLocations(mapperXml);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setLogImpl(StdOutImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    @Bean(name = "commonSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate1(@Qualifier("commonSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

}
