package com.itheima.service.impl;

import com.itheima.mapper.ShopCategoryMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Category;
import com.itheima.service.ShopCategoryService;
import com.itheima.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


////对应的service方法创建相应的接口
@Service
public class ShopCategoryServiceImpl implements ShopCategoryService {

    @Autowired
    private ShopCategoryMapper categoryMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Category category) {
        //补充属性值
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String username=userMapper.findUsernameById(userId);
        category.setCreateUser(username);

        categoryMapper.add(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> list() {
        //获取所有的分类信息
        /*Map<String, Object> map = ThreadLocalUtil.get();
        Integer createUser = (Integer) map.get("id");*/
        return categoryMapper.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Category findById(Integer id) {
        Category c =categoryMapper.findById(id);
        return c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Category category) {
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.update(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        categoryMapper.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> controllerList() {
        //获取管理员所创建的分类信息
        return categoryMapper.list();
    }

    //查询分类名称
    @Override
    @Transactional(readOnly = true)
    public String findCategoryNameById(Integer id) {
        return categoryMapper.findCategoryNameById(id);
    }
}
