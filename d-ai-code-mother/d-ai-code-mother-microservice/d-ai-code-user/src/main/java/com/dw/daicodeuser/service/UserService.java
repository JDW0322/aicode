package com.dw.daicodeuser.service;

import com.dw.daicodemother.model.dto.user.UserQueryRequest;
import com.dw.daicodemother.model.entity.User;
import com.dw.daicodemother.model.vo.LoginUserVO;
import com.dw.daicodemother.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;


import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/dw0322">dw</a>
 */
public interface UserService extends IService<User> {


    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);


    String getEncryptPassword(String userPassword);
}
