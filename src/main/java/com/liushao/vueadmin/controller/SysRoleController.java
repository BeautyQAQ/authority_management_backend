package com.liushao.vueadmin.controller;

import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.model.AdminRequests;
import com.liushao.vueadmin.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys/role")
public class SysRoleController {
  private final AdminService service;

  public SysRoleController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/list")
  @PreAuthorize("hasAuthority('sys:role:list')")
  public Result list(
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "1") int current,
      @RequestParam(defaultValue = "10") int size) {
    return Result.succ(service.roles(name, current, size));
  }

  @GetMapping("/options")
  @PreAuthorize("hasAuthority('sys:user:role')")
  public Result options() {
    return Result.succ(service.roleOptions());
  }

  @GetMapping("/info/{id}")
  @PreAuthorize("hasAnyAuthority('sys:role:list','sys:role:update','sys:role:perm')")
  public Result info(@PathVariable long id) {
    return Result.succ(service.role(id));
  }

  @PostMapping("/save")
  @PreAuthorize("hasAuthority('sys:role:save')")
  public Result save(@Valid @RequestBody AdminRequests.Role input) {
    return Result.succ(service.saveRole(input, false));
  }

  @PostMapping("/update")
  @PreAuthorize("hasAuthority('sys:role:update')")
  public Result update(@Valid @RequestBody AdminRequests.Role input) {
    return Result.succ(service.saveRole(input, true));
  }

  @PostMapping("/delete")
  @PreAuthorize("hasAuthority('sys:role:delete')")
  public Result delete(@RequestBody List<Long> ids) {
    service.deleteRoles(ids);
    return Result.succ(null);
  }

  @PostMapping("/perm/{id}")
  @PreAuthorize("hasAuthority('sys:role:perm')")
  public Result menus(@PathVariable long id, @RequestBody List<Long> ids) {
    service.assignMenus(id, ids);
    return Result.succ(null);
  }
}
