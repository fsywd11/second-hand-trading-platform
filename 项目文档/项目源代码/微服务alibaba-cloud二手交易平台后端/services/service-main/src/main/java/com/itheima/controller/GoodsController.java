package com.itheima.controller;

import org.example.goods.DTO.GoodsDTO;
import org.example.goods.DTO.GoodsQueryDTO;
import com.itheima.anno.PreAuthorize;
import com.itheima.pojo.Enum.GoodsStatusEnum;
import org.example.common.PageBean;
import org.example.common.Result;
import com.itheima.service.GoodsService;
import com.itheima.util.ThreadLocalUtil;
import org.example.user.VO.BuyerViewSellerVO;
import org.example.goods.VO.GoodsDetailVO;
import org.example.goods.VO.GoodsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/goods")
@Slf4j
@Tag(name = "商品接口", description = "二手商品管理接口")
public class GoodsController {

    @Resource
    private GoodsService goodsService;

    // ==================== 基础 CURD ====================
    @PreAuthorize("/goods/add")
    @PostMapping("/add")
    @Operation(summary = "新增商品", description = "发布二手商品")
    public Result add(@RequestBody @Validated GoodsDTO goodsDTO) {
        try {
            goodsService.add(goodsDTO);
            return Result.success("商品发布成功");
        } catch (RuntimeException e) {
            log.error("新增商品失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("/goods/list")
    @PostMapping("/list")
    @Operation(summary = "分页查询商品", description = "多条件筛选二手商品（后台管理）")
    public Result<PageBean<GoodsVO>> list(@RequestBody GoodsQueryDTO queryDTO) {
        try {
            PageBean<GoodsVO> pb = goodsService.alllist(queryDTO);
            return Result.success(pb);
        } catch (Exception e) {
            log.error("分页查询商品失败", e);
            return Result.error("查询商品失败：" + e.getMessage());
        }
    }

    @PostMapping("/detail/{id}")
    @Operation(summary = "商品详情", description = "查询商品详细信息")
    public Result<GoodsDetailVO> detail(@PathVariable Integer id) {
        try {
            if (id == null || id < 1) {
                return Result.error("商品ID不合法");
            }
            GoodsDetailVO vo = goodsService.findById(id);
            if (vo == null) {
                return Result.error("商品不存在");
            }
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询商品详情失败，ID：{}", id, e);
            return Result.error("查询商品详情失败：" + e.getMessage());
        }
    }

    @PreAuthorize("/goods/update")
    @PostMapping("/update")
    @Operation(summary = "修改商品", description = "更新商品信息")
    public Result update(@RequestBody @Validated GoodsDTO goodsDTO) {
        try {
            if (goodsDTO.getId() == null || goodsDTO.getId() < 1) {
                return Result.error("商品ID不能为空且必须为正整数");
            }
            goodsService.update(goodsDTO);
            return Result.success("商品更新成功");
        } catch (RuntimeException e) {
            log.error("修改商品失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("修改商品异常", e);
            return Result.error("商品更新失败：" + e.getMessage());
        }
    }

    @PreAuthorize("/goods/delete")
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除商品", description = "下架并删除商品（同步清理向量数据）")
    public Result delete(@PathVariable Integer id) {
        try {
            if (id == null || id < 1) {
                return Result.error("商品ID不合法");
            }
            goodsService.delete(id);
            return Result.success("商品删除成功");
        } catch (Exception e) {
            log.error("删除商品失败，ID：{}", id, e);
            return Result.error("商品删除失败：" + e.getMessage());
        }
    }

    @PreAuthorize("/goods/updateStatus")
    @PostMapping("/updateStatus/{id}/{status}")
    @Operation(summary = "更新商品状态", description = "在售/已售罄/下架/审核中/违规封禁")
    public Result updateStatus(
            @PathVariable Integer id,
            @Parameter(description = "状态码：1-在售 2-已售罄 3-下架 4-审核中 5-违规封禁")
            @PathVariable Integer status) {
        try {
            if (id == null || id < 1) {
                return Result.error("商品ID不合法");
            }
            boolean statusValid = false;
            for (GoodsStatusEnum enumItem : GoodsStatusEnum.values()) {
                if (enumItem.getCode().equals(status)) {
                    statusValid = true;
                    break;
                }
            }
            if (!statusValid) {
                return Result.error("商品状态码不合法，允许值：1-在售 2-已售罄 3-下架 4-审核中 5-违规封禁");
            }

            goodsService.updateStatus(id, status);
            return Result.success("商品状态更新成功");
        } catch (RuntimeException e) {
            log.error("更新商品状态失败，ID：{}，状态：{}", id, status, e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新商品状态异常，ID：{}，状态：{}", id, status, e);
            return Result.error("商品状态更新失败：" + e.getMessage());
        }
    }

    // ==================== 商品列表相关 ====================
    @PostMapping("/goodsopenlist")
    @Operation(summary = "公开商品列表", description = "分页查询仅在售的二手商品")
    public Result<PageBean<GoodsVO>> goodsOpenList(@RequestBody GoodsQueryDTO queryDTO) {
        try {
            queryDTO.setGoodsStatus(GoodsStatusEnum.ON_SALE.getCode());
            PageBean<GoodsVO> pb = goodsService.list(queryDTO);
            return Result.success(pb);
        } catch (Exception e) {
            log.error("查询公开商品列表失败", e);
            return Result.error("查询公开商品失败：" + e.getMessage());
        }
    }

    @PreAuthorize("/goods/mylist")
    @PostMapping("/mylist")
    @Operation(summary = "我的商品列表", description = "分页查询当前登录用户发布的商品")
    public Result<PageBean<GoodsVO>> myList(@RequestBody GoodsQueryDTO queryDTO) {
        try {
            Map<String, Object> map = ThreadLocalUtil.get();
            Integer userId = (Integer) map.get("id");
            if (userId == null) {
                return Result.error("用户未登录");
            }
            queryDTO.setSellerId(userId);
            PageBean<GoodsVO> pb = goodsService.alllist(queryDTO);
            return Result.success(pb);
        } catch (Exception e) {
            log.error("查询我的商品列表失败", e);
            return Result.error("查询我的商品失败：" + e.getMessage());
        }
    }

    @PostMapping("/findSellerByUserId/{id}")
    @Operation(summary = "查询商品卖家信息", description = "通过用户id查询卖家基本信息")
    public Result<BuyerViewSellerVO> findSellerByUserId(@PathVariable Integer id) {
        try {
            if (id == null || id < 1) {
                return Result.error("用户ID不合法");
            }
            BuyerViewSellerVO vo = goodsService.findSellerByUserId(id);
            if (vo == null) {
                return Result.error("卖家信息不存在");
            }
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询卖家信息失败，用户ID：{}", id, e);
            return Result.error("查询卖家信息失败：" + e.getMessage());
        }
    }

    @PostMapping("/seller/alllist")
    @Operation(summary = "卖家商品列表", description = "分页查询指定卖家发布的商品")
    public Result<PageBean<GoodsVO>> sellerAllList(@RequestBody GoodsQueryDTO queryDTO) {
        return Result.success(goodsService.alllist(queryDTO));
    }

    // ==================== 内部调用接口（来自第一个Controller，无重复） ====================
    @GetMapping("/internal/{id}")
    @Operation(summary = "内部商品详情", description = "服务间调用专用")
    public Result<GoodsDetailVO> internalDetail(@PathVariable Integer id) {
        return Result.success(goodsService.findById(id));
    }

    @PostMapping("/internal/listByIds")
    @Operation(summary = "根据ID批量查询商品", description = "服务间批量获取商品信息")
    public Result<List<GoodsVO>> listByIds(@RequestBody List<Integer> ids) {
        return Result.success(goodsService.listByIds(ids));
    }

    // ==================== SQL 搜索（主搜索路径，零外部依赖） ====================
    @PostMapping("/search")
    @Operation(summary = "SQL搜索商品", description = "关键词模糊匹配 + 分类 + 价格区间 + 分页排序，纯数据库查询")
    public Result<PageBean<GoodsVO>> search(@RequestBody GoodsQueryDTO queryDTO) {
        try {
            PageBean<GoodsVO> pb = goodsService.search(queryDTO);
            return Result.success(pb);
        } catch (Exception e) {
            log.error("SQL搜索失败，keyword：{}", queryDTO.getKeyword(), e);
            return Result.error("商品搜索失败");
        }
    }

    // ==================== RAG & 推荐 & 向量库（第二个Controller独有） ====================
    @PostMapping("/rag/search")
    @Operation(summary = "RAG搜索", description = "通过语义检索相似商品并生成总结")
    public Result<Map<String, Object>> ragSearch(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }
            // 通过 Feign 调用 service-product 的完整 RAG 搜索（含向量检索 + AI 摘要）
            Map<String, Object> result = goodsService.ragSearchWithSummary(query.trim());
            return Result.success(result);
        } catch (Exception e) {
            log.error("RAG搜索失败，查询词：{}", request.get("query"), e);
            return Result.error("商品搜索失败：" + e.getMessage());
        }
    }

    @PostMapping("/recommend/collaborative")
    @Operation(summary = "协同过滤推荐", description = "根据用户收藏历史，推荐相似用户群体也喜欢的商品")
    public Result<List<GoodsVO>> collaborativeRecommend(@RequestBody Map<String, Object> params) {
        try {
            Integer userId = params.get("userId") != null ? (Integer) params.get("userId") : null;
            int limit = params.get("limit") != null ? (Integer) params.get("limit") : 10;
            List<GoodsVO> list = goodsService.collaborativeRecommend(userId, limit);
            return Result.success(list);
        } catch (Exception e) {
            log.error("协同过滤推荐失败", e);
            return Result.error("推荐失败：" + e.getMessage());
        }
    }

    @PreAuthorize("/goods/cleanMilvusDirtyData")
    @PostMapping("/cleanMilvusDirtyData")
    @Operation(summary = "清理Milvus脏数据", description = "全量校验并删除向量库中的无效数据（仅管理员）")
    public Result cleanMilvusDirtyData() {
        try {
            goodsService.cleanMilvusDirtyData();
            return Result.success("Milvus脏数据清理完成");
        } catch (Exception e) {
            log.error("清理Milvus脏数据失败", e);
            return Result.error("清理脏数据失败：" + e.getMessage());
        }
    }

    @PostMapping("/recommend/byKeyword")
    @Operation(summary = "按关键词推荐商品", description = "协同过滤推荐（基于收藏行为）+ 关键词搜索混合")
    public Result<Map<String, Object>> recommendByKeyword(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("keyword");
            if (query == null || query.trim().isEmpty()) {
                return Result.error("查询内容不能为空");
            }

            // 1. 协同过滤（如果用户已登录）
            List<GoodsVO> cfList = new ArrayList<>();
            try {
                java.util.Map<String, Object> threadLocal = ThreadLocalUtil.get();
                if (threadLocal != null && threadLocal.get("id") != null) {
                    Integer userId = (Integer) threadLocal.get("id");
                    cfList = goodsService.collaborativeRecommend(userId, 5);
                }
            } catch (Exception e) {
                log.debug("协同过滤不可用，降级: {}", e.getMessage());
            }

            // 2. 关键词搜索
            GoodsQueryDTO dto = new GoodsQueryDTO();
            dto.setKeyword(query.trim());
            dto.setPageNum(1);
            dto.setPageSize(10);
            dto.setSortField("time");
            PageBean<GoodsVO> page = goodsService.search(dto);
            List<GoodsVO> searchList = page.getItems();

            // 3. 合并去重（CF 在前，搜索在后）
            java.util.Set<Integer> seenIds = new java.util.HashSet<>();
            List<GoodsVO> mergedList = new ArrayList<>();
            for (GoodsVO g : cfList) {
                if (seenIds.add(g.getId())) {
                    mergedList.add(g);
                }
            }
            for (GoodsVO g : searchList) {
                if (seenIds.add(g.getId())) {
                    mergedList.add(g);
                }
            }

            // 取前 10
            List<GoodsVO> finalList = mergedList.subList(0, Math.min(10, mergedList.size()));

            Map<String, Object> result = new HashMap<>();
            result.put("summary", "推荐商品（协同过滤 + 关键词）");
            result.put("goodsList", finalList);
            result.put("total", finalList.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("推荐商品失败，关键词：{}", request.get("keyword"), e);
            return Result.error("推荐商品查询失败：" + e.getMessage());
        }
    }
}
