package com.paris.pojo;

import lombok.Data;

/**
 * @ClassName Dict
 * @Description 数据字典实体
 * @Author langjiao
 * @Date 2023/6/21 10:30
 * @Version 1.0
 */
@Data
public class Dict {
    /**
     * 主键ID
     */
    private Long dictId;
    /**
     * 字典父ID
     */
    private Long dictParentId;
    /**
     * 层级
     */
    private Integer level;
    /**
     * 字典编码
     */
    private String dictCode;
    /**
     * 字典名称
     */
    private String dictName;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 状态
     */
    private Integer status;
}
