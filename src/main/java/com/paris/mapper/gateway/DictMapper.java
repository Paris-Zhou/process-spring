package com.paris.mapper.gateway;

import com.paris.pojo.Dict;
import org.apache.ibatis.annotations.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(value = "gatewayTransactionManager",rollbackFor = RuntimeException.class)
public interface DictMapper {

    Dict selectOne(@Param("dictId") Long dictId);

    List<Dict> selectList(@Param("parentDictId") Long parentDictId);

}
