package com.liushao.vueadmin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @author huangshen
 * @since 2021-11-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String name;

    private String code;

    /**
     * 备注
     */
    private String remark;


}
