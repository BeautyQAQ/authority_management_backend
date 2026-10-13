package com.liushao.vueadmin.controller;

import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.model.AdminRequests;
import com.liushao.vueadmin.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys/user")
public class SysUserController {
  private final AdminService service;

  public SysUserController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/list")
  @PreAuthorize("hasAuthority('sys:user:list')")
  public Result list(
      @RequestParam(defaultValue = "") String username,
      @RequestParam(defaultValue = "1") int current,
      @RequestParam(defaultValue = "10") int size) {
    return Result.succ(service.users(username, current, size));
  }

  @GetMapping("/info/{id}")
  @PreAuthorize("hasAnyAuthority('sys:user:list','sys:user:update','sys:user:role')")
  public Result info(@PathVariable long id) {
    return Result.succ(service.user(id));
  }

  @PostMapping("/save")
  @PreAuthorize("hasAuthority('sys:user:save')")
  public Result save(@Valid @RequestBody AdminRequests.User input) {
    return Result.succ(service.saveUser(input, false));
  }

  @PostMapping("/update")
  @PreAuthorize("hasAuthority('sys:user:update')")
  public Result update(@Valid @RequestBody AdminRequests.User input) {
    return Result.succ(service.saveUser(input, true));
  }

  @PostMapping("/delete")
  @PreAuthorize("hasAuthority('sys:user:delete')")
  public Result delete(@RequestBody List<Long> ids) {
    service.deleteUsers(ids);
    return Result.succ(null);
  }

  @PostMapping("/role/{id}")
  @PreAuthorize("hasAuthority('sys:user:role')")
  public Result roles(@PathVariable long id, @RequestBody List<Long> ids) {
    service.assignRoles(id, ids);
    return Result.succ(null);
  }

  @PostMapping("/repass")
  @PreAuthorize("hasAuthority('sys:user:repass')")
  public Result reset(@Valid @RequestBody AdminRequests.ResetPassword input) {
    service.resetPassword(input);
    return Result.succ(null);
  }

  @PostMapping("/updatePass")
  public Result password(@Valid @RequestBody AdminRequests.Password input) {
    service.changePassword(input);
    return Result.succ(null);
  }
}
