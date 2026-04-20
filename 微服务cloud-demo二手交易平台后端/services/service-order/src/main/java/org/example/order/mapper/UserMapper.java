package org.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.user.POJO.User;

@Mapper
public interface UserMapper {

    @Select("select * from user where id = #{id}")
    User findById(Integer id);
}
