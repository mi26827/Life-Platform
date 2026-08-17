package com.study.lifeplatform.service.impl;

import com.study.lifeplatform.entity.UserInfo;
import com.study.lifeplatform.mapper.UserInfoMapper;
import com.study.lifeplatform.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author mi
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
