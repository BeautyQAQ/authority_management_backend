package com.liushao.vueadmin.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.liushao.vueadmin.common.exception.ApiException;
import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.service.AdminService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/community")
public class CommunityAdminController {
  private final CommunityAdminBridge bridge;
  public CommunityAdminController(CommunityAdminBridge bridge) { this.bridge = bridge; }
  private ResponseEntity<Result> call(HttpMethod method, String path, Map<String,String> query, Object body) {
    return bridge.forward(method, path, query, body, AdminService.principal().username());
  }
  private static long id(long value) { if (value < 1) throw new ApiException(400, "记录 ID 不正确"); return value; }
  private static String reason(String value) {
    if (value == null || value.isBlank() || value.length() > 255) throw new ApiException(400, "请填写操作原因（1 至 255 个字符）");
    return value;
  }
  private static Map<String,String> page(int pageNo, int size, String query) {
    if (pageNo < 1 || pageNo > 1000000 || size < 1 || size > 50 || query.length() > 100) throw new ApiException(400, "分页或搜索参数不正确");
    var result = new LinkedHashMap<String,String>();
    result.put("pageNo", String.valueOf(pageNo)); result.put("size", String.valueOf(size)); result.put("query", query);
    return result;
  }

  @GetMapping("/users") @PreAuthorize("hasAuthority('community:user:list')")
  public ResponseEntity<Result> users(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="") String query) { return call(HttpMethod.GET,"users",page(pageNo,size,query),null); }
  @GetMapping("/users/{id}") @PreAuthorize("hasAuthority('community:user:list')")
  public ResponseEntity<Result> user(@PathVariable long id) { return call(HttpMethod.GET,"users/"+id(id),Map.of(),null); }
  @PutMapping("/users/{id}/status") @PreAuthorize("hasAuthority('community:user:status')")
  public ResponseEntity<Result> userStatus(@PathVariable long id, @Valid @RequestBody CommunityRequests.UserStatus body) {
    return call(HttpMethod.PUT,"users/"+id(id)+"/status",Map.of(),body);
  }

  @GetMapping("/posts") @PreAuthorize("hasAuthority('community:post:list')")
  public ResponseEntity<Result> posts(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="") String query, @RequestParam(defaultValue="") String status) {
    var params=page(pageNo,size,query);
    if (!status.isEmpty()) {
      if (!Set.of("PUBLISHED","PENDING","HIDDEN").contains(status)) throw new ApiException(400,"帖子状态不正确");
      params.put("status",status);
    }
    return call(HttpMethod.GET,"posts",params,null);
  }
  @GetMapping("/posts/{id}") @PreAuthorize("hasAuthority('community:post:list')")
  public ResponseEntity<Result> post(@PathVariable long id) { return call(HttpMethod.GET,"posts/"+id(id),Map.of(),null); }
  @PutMapping("/posts/{id}/moderation") @PreAuthorize("hasAuthority('community:post:moderate')")
  public ResponseEntity<Result> moderatePost(@PathVariable long id, @Valid @RequestBody CommunityRequests.Moderation body) {
    return call(HttpMethod.PUT,"posts/"+id(id)+"/moderation",Map.of(),body);
  }
  @DeleteMapping("/posts/{id}") @PreAuthorize("hasAuthority('community:post:delete')")
  public ResponseEntity<Result> deletePost(@PathVariable long id, @RequestParam String reason) {
    return call(HttpMethod.DELETE,"posts/"+id(id),Map.of("reason",reason(reason)),null);
  }

  @GetMapping("/comments") @PreAuthorize("hasAuthority('community:comment:list')")
  public ResponseEntity<Result> comments(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="") String query, @RequestParam(required=false) Long postId) {
    var params=page(pageNo,size,query); if(postId!=null) params.put("postId",String.valueOf(id(postId)));
    return call(HttpMethod.GET,"comments",params,null);
  }
  @GetMapping("/comments/{id}") @PreAuthorize("hasAuthority('community:comment:list')")
  public ResponseEntity<Result> comment(@PathVariable long id) { return call(HttpMethod.GET,"comments/"+id(id),Map.of(),null); }
  @PutMapping("/comments/{id}/visibility") @PreAuthorize("hasAuthority('community:comment:moderate')")
  public ResponseEntity<Result> visibility(@PathVariable long id, @Valid @RequestBody CommunityRequests.Visibility body) {
    return call(HttpMethod.PUT,"comments/"+id(id)+"/visibility",Map.of(),body);
  }
  @DeleteMapping("/comments/{id}") @PreAuthorize("hasAuthority('community:comment:delete')")
  public ResponseEntity<Result> deleteComment(@PathVariable long id, @RequestParam String reason) {
    return call(HttpMethod.DELETE,"comments/"+id(id),Map.of("reason",reason(reason)),null);
  }

  @GetMapping("/{resource:billboards|tips|promotions|tags}")
  @PreAuthorize("@communityPermissions.allowed(authentication,#resource,'list')")
  public ResponseEntity<Result> catalog(@PathVariable String resource, @RequestParam(defaultValue="1") int pageNo,
      @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String query) {
    return call(HttpMethod.GET,resource,page(pageNo,size,query),null);
  }
  @GetMapping("/{resource:billboards|tips|promotions|tags}/{id}")
  @PreAuthorize("@communityPermissions.allowed(authentication,#resource,'list')")
  public ResponseEntity<Result> item(@PathVariable String resource,@PathVariable long id) {
    return call(HttpMethod.GET,resource+"/"+id(id),Map.of(),null);
  }
  @PostMapping("/{resource:billboards|tips|promotions|tags}")
  @PreAuthorize("@communityPermissions.allowed(authentication,#resource,'create')")
  public ResponseEntity<Result> create(@PathVariable String resource,@RequestBody JsonNode body) {
    return call(HttpMethod.POST,resource,Map.of(),CommunityRequests.catalog(resource,body));
  }
  @PutMapping("/{resource:billboards|tips|promotions|tags}/{id}")
  @PreAuthorize("@communityPermissions.allowed(authentication,#resource,'update')")
  public ResponseEntity<Result> update(@PathVariable String resource,@PathVariable long id,@RequestBody JsonNode body) {
    return call(HttpMethod.PUT,resource+"/"+id(id),Map.of(),CommunityRequests.catalog(resource,body));
  }
  @DeleteMapping("/{resource:billboards|tips|promotions|tags}/{id}")
  @PreAuthorize("@communityPermissions.allowed(authentication,#resource,'delete')")
  public ResponseEntity<Result> delete(@PathVariable String resource,@PathVariable long id) {
    return call(HttpMethod.DELETE,resource+"/"+id(id),Map.of(),null);
  }
  @GetMapping("/audit") @PreAuthorize("hasAuthority('community:audit:list')")
  public ResponseEntity<Result> audit(@RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="") String query) { return call(HttpMethod.GET,"audit",page(pageNo,size,query),null); }
}
