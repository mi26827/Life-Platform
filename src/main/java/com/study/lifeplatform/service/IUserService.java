package com.study.lifeplatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.study.lifeplatform.dto.LoginFormDTO;
import com.study.lifeplatform.dto.Result;
import com.study.lifeplatform.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author mi
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
