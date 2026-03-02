package com.itheima.service;

import com.itheima.pojo.Category;

import java.util.List;

public interface ShopCategoryService {
    //新增分类
    void add(Category category);
    //列表查询
    List<Category> list();
    //根据分类名称id查询分类信息
    Category findById(Integer id);
    //更新分类信息
    void update(Category category);
    //删除分类信息
    void delete(Integer id);
    //获取管理员账户id为1的所有分类信息
    List<Category> controllerList();
    //查询分类名称
    String findCategoryNameById(Integer id);
}
