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
public class SysRoleMenu {

    private static final long serialVersionUID = 1L;

    private Long roleId;

    private Long menuId;


}
