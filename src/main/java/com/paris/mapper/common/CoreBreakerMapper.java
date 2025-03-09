package com.paris.mapper.common;

import com.paris.pojo.CoreBreakerEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * @title: CoreBreakerMapper
 * @Description TODO
 * @Author Administrator
 * @Date 2024/3/6 15:29
 */
@Transactional(value = "commonTransactionManager",rollbackFor = RuntimeException.class)
public interface CoreBreakerMapper {

    List<CoreBreakerEntity> getByBreakerCode(@Param("breakerCodes") Collection<String> breakerCodes);

}
