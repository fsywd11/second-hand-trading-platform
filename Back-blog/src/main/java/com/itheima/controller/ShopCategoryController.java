package com.itheima.controller;

import com.itheima.anno.PreAuthorize;
import com.itheima.pojo.Category;
import com.itheima.pojo.Result;
import com.itheima.service.ShopCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shopcategory")
@Slf4j
public class ShopCategoryController {

    @Autowired
    private ShopCategoryService categoryService;

    //添加商品分类
    @PreAuthorize("/shopcategory/add")
    @PostMapping
    public Result add(@RequestBody @Validated(Category.Add.class) Category category) {
        categoryService.add(category);
        return Result.success("添加成功");
    }

    //获取全部商品分类
    @PreAuthorize("/shopcategory/list")
    @GetMapping
    public Result<List<Category>> list() {
        List<Category> cs = categoryService.list();
        return Result.success(cs);
    }

    //根据id查询商品分类信息
    @PreAuthorize("/shopcategory/detail")
    @GetMapping("/detail")
    public Result<Category> detail(Integer id) {
        Category c = categoryService.findById(id);
        return Result.success(c);
    }

    //修改商品分类信息
    @PreAuthorize("/shopcategory/update")
    @PutMapping
    public Result update(@RequestBody @Validated(Category.Update.class) Category category) {
        categoryService.update(category);
        return Result.success();
    }

    //删除商品分类
    @PreAuthorize("/shopcategory/delete")
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<Category>> openList() {
        List<Category> cs = categoryService.list();
        return Result.success(cs);
    }
}
