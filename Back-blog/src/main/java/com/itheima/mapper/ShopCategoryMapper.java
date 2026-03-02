package com.itheima.mapper;

import com.itheima.pojo.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

//对应的service方法创建相应的接口
@Mapper
public interface ShopCategoryMapper {
    //新增分类
    @Insert("insert into shop_category (category_name,category_alias,create_user,create_time,update_time)" +
              "values(#{categoryName},#{categoryAlias},#{createUser},#{createTime},#{updateTime})")
    void add(Category category);
    //查询所有分类
    @Select("select * from shop_category")
    List<Category> list();
    //根据id查询分类
    @Select("select * from shop_category where id = #{id}")
    Category findById(Integer id);
    //更新分类信息
    @Update("update shop_category set category_name = #{categoryName},category_alias = #{categoryAlias},update_time = #{updateTime} where id = #{id}")
    void update(Category category);
    //删除分类信息
    @Delete("delete from shop_category where id = #{id}")
    void delete(Integer id);

    @Select("select category_name from category where id = #{id}")
    String findCategoryNameById(Integer id);
}
