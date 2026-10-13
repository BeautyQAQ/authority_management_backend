package com.liushao.vueadmin.controller;

import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.model.AdminRequests;
import com.liushao.vueadmin.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {
  private final AdminService service;

  public SysMenuController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/nav")
  public Result nav() {
    return Result.succ(service.navigation(AdminService.principal().id()));
  }

  @GetMapping("/list")
  @PreAuthorize("hasAnyAuthority('sys:menu:list','sys:role:perm')")
  public Result list() {
    return Result.succ(service.menuTree());
  }

  @GetMapping("/info/{id}")
  @PreAuthorize("hasAnyAuthority('sys:menu:list','sys:menu:update')")
  public Result info(@PathVariable long id) {
    return Result.succ(service.menu(id));
  }

  @PostMapping("/save")
  @PreAuthorize("hasAuthority('sys:menu:save')")
  public Result save(@Valid @RequestBody AdminRequests.Menu input) {
    return Result.succ(service.saveMenu(input, false));
  }

  @PostMapping("/update")
  @PreAuthorize("hasAuthority('sys:menu:update')")
  public Result update(@Valid @RequestBody AdminRequests.Menu input) {
    return Result.succ(service.saveMenu(input, true));
  }

  @PostMapping("/delete/{id}")
  @PreAuthorize("hasAuthority('sys:menu:delete')")
  public Result delete(@PathVariable long id) {
    service.deleteMenu(id);
    return Result.succ(null);
  }
}
