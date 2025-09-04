package com.dw.daicodemother.service;


import com.dw.daicodemother.model.dto.app.AppAddRequest;
import com.dw.daicodemother.model.dto.app.AppQueryRequest;
import com.dw.daicodemother.model.entity.App;
import com.dw.daicodemother.model.entity.User;
import com.dw.daicodemother.model.vo.AppVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/dw0322">dw</a>
 */
public interface AppService extends IService<App> {

    Long createApp(AppAddRequest appAddRequest, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);

    String deployApp(Long appId, User loginUser);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 获取查询包装器
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 创建应用
     */
    Long createApp(App app, HttpServletRequest request);

    /**
     * 更新应用
     */
    boolean updateApp(App app, HttpServletRequest request);

    /**
     * 删除应用
     */
    boolean deleteApp(Long id, HttpServletRequest request);

    /**
     * 分页查询用户自己的应用列表
     */
    Page<AppVO> listMyAppsByPage(AppQueryRequest appQueryRequest, HttpServletRequest request);

    /**
     * 分页查询精选应用列表
     */
    Page<AppVO> listFeaturedAppsByPage(AppQueryRequest appQueryRequest);

    /**
     * 管理员分页查询应用列表
     */
    Page<AppVO> listAppsByPage(AppQueryRequest appQueryRequest);

    /**
     * 根据id获取应用详情
     */
    App getAppById(Long id, HttpServletRequest request);
}
