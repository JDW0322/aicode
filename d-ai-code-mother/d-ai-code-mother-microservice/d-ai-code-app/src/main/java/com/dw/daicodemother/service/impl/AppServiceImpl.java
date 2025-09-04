package com.dw.daicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.dw.daicodemother.ai.AiCodeGenTypeRoutingService;
import com.dw.daicodemother.ai.AiCodeGenTypeRoutingServiceFactory;

import com.dw.daicodemother.core.AiCodeGeneratorFacade;
import com.dw.daicodemother.core.builder.VueProjectBuilder;
import com.dw.daicodemother.core.handler.StreamHandlerExecutor;

import com.dw.daicodemother.innerservice.InnerScreenshotService;
import com.dw.daicodemother.innerservice.InnerUserService;
import com.dw.daicodemother.mapper.AppMapper;

import com.dw.daicodemother.service.AppService;
import com.dw.daicodemother.service.ChatHistoryService;

import com.dw.daicodemother.constant.AppConstant;
import com.dw.daicodemother.constant.UserConstant;
import com.dw.daicodemother.exception.BusinessException;
import com.dw.daicodemother.exception.ErrorCode;
import com.dw.daicodemother.exception.ThrowUtils;
import com.dw.daicodemother.model.dto.app.AppAddRequest;
import com.dw.daicodemother.model.dto.app.AppQueryRequest;
import com.dw.daicodemother.model.entity.App;
import com.dw.daicodemother.model.entity.User;
import com.dw.daicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.dw.daicodemother.model.enums.CodeGenTypeEnum;
import com.dw.daicodemother.model.vo.AppVO;
import com.dw.daicodemother.model.vo.UserVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/dw0322">dw</a>
 */

@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {



    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;


    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @DubboReference
    private InnerUserService userService;

    @DubboReference
    private InnerScreenshotService screenshotService;




    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }


    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }





    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }


    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;

        // 9. 返回可访问的 URL
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集AI响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream,chatHistoryService,appId,loginUser,codeGenTypeEnum);
    }


    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }


    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    @Override
    public Long createApp(App app, HttpServletRequest request) {
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用信息为空");
        }
        
        // 获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        
        // 设置应用信息
        app.setUserId(loginUser.getId());
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        app.setEditTime(LocalDateTime.now());
        
        // 保存应用
        boolean result = this.save(app);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建应用失败");
        }
        
        return app.getId();
    }

    @Override
    public boolean updateApp(App app, HttpServletRequest request) {
        if (app == null || app.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用信息为空");
        }
        
        // 获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        
        // 检查应用是否存在
        App oldApp = this.getById(app.getId());
        if (oldApp == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        
        // 如果不是管理员，只能修改自己的应用
        if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            if (!oldApp.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此应用");
            }
            // 普通用户只能修改应用名称
            app.setAppName(oldApp.getAppName());
            app.setCover(oldApp.getCover());
            app.setPriority(oldApp.getPriority());
        }
        
        app.setUpdateTime(LocalDateTime.now());
        app.setEditTime(LocalDateTime.now());
        
        return this.updateById(app);
    }

    @Override
    public boolean deleteApp(Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用ID无效");
        }
        
        // 获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        
        // 检查应用是否存在
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        
        // 如果不是管理员，只能删除自己的应用
        if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            if (!app.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除此应用");
            }
        }
        
        return this.removeById(id);
    }

    @Override
    public Page<AppVO> listMyAppsByPage(AppQueryRequest appQueryRequest, HttpServletRequest request) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        User loginUser = InnerUserService.getLoginUser(request);
        appQueryRequest.setUserId(loginUser.getId());

        if (appQueryRequest.getPageSize() > 20) {
            appQueryRequest.setPageSize(20);
        }

        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        Page<App> appPage = this.page(new Page<>(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()), queryWrapper);

        // 手动转换 Page<App> 到 Page<AppVO>
        Page<AppVO> voPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        List<AppVO> voList = appPage.getRecords().stream()
                .map(this::getAppVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }


    @Override
    public Page<AppVO> listFeaturedAppsByPage(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        
        // 限制每页最多20个
        if (appQueryRequest.getPageSize() > 20) {
            appQueryRequest.setPageSize(20);
        }
        
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        // 精选应用按优先级和创建时间排序
        queryWrapper.orderBy("priority", false).orderBy("createTime", false);
        
        Page<App> appPage = this.page(new Page<>(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()), queryWrapper);

        // 手动转换 Page<App> 到 Page<AppVO>
        Page<AppVO> voPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        List<AppVO> voList = appPage.getRecords().stream()
                .map(this::getAppVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public Page<AppVO> listAppsByPage(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        Page<App> appPage = this.page(new Page<>(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()), queryWrapper);

        // 手动转换 Page<App> 到 Page<AppVO>
        Page<AppVO> voPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        List<AppVO> voList = appPage.getRecords().stream()
                .map(this::getAppVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public App getAppById(Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用ID无效");
        }
        
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        
        // 获取当前登录用户
        User loginUser = InnerUserService.getLoginUser(request);
        
        // 如果不是管理员，只能查看自己的应用
        if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            if (!app.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看此应用");
            }
        }
        
        return app;
    }
}
