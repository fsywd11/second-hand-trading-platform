<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import QuillEditor from '@/components/QuillEditor.vue'
import { goodsAddService } from '@/api/goods.js'
import { shopCategoryListService } from '@/api/shopcategory.js'
import { useTokenStore } from '@/stores/token.js'
import useUserInfoStore from '@/stores/userInfo.js'

const userInfoStore = useUserInfoStore()
const router = useRouter()
const tokenStore = useTokenStore()
// 商品分类列表
const categorys = ref([])
// 封面图文件（单文件）
const coverFile = ref(null)
// 封面图URL
const coverUrl = ref('')
// 详情图文件列表
const detailFileList = ref([])
// 详情图URL列表
const detailUrlList = ref([])

// 定义新旧程度选项（与后端GoodsIsNewEnum完全一致）
const newDegreeOptions = ref([
  { label: '二手', value: 0 },
  { label: '全新', value: 1 },
  { label: '9成新', value: 2 },
  { label: '8成新', value: 3 },
  { label: '7成新及以下', value: 4 }
])

// 商品表单模型 - 适配后端GoodsDTO
const goodsModel = ref({
  id: '',
  goodsName: '',
  categoryId: '',
  originalPrice: 0,
  sellPrice: 0,
  isNew: 0, // 默认二手（对应后端枚举）
  goodsStatus: 1, // 默认在售
  goodsDesc: '',
  goodsPic: '', // 封面图（单个）
  stock: 1,
  sellerId: userInfoStore.info.id || null,
  imageList: [] // 详情图列表
})

// 获取商品分类列表
const getCategoryList = async () => {
  try {
    const result = await shopCategoryListService()
    categorys.value = result.data
  } catch (error) {
    ElMessage.error('获取商品分类失败')
  }
}

// 封面图选择回调（覆盖式）
const coverInput = ref(null)
const handleCoverChange = async (file) => {
  if (!file) return
  const maxSize = 5 * 1024 * 1024 // 5MB
  if (file.size > maxSize) {
    ElMessage.warning('封面图大小不能超过5MB')
    return
  }
  coverFile.value = file
  // 模拟上传（实际替换为你的上传接口）
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch('http://localhost:8080/upload', {
      method: 'POST',
      headers: {
        Authorization: tokenStore.token
      },
      body: formData
    })
    const res = await response.json()
    const url = res.data.url
    coverUrl.value = url
    goodsModel.value.goodsPic = url
    ElMessage.success('封面图上传成功')
  } catch (error) {
    ElMessage.error('封面图上传失败，请重试')
    coverFile.value = null
    coverUrl.value = ''
  }
}

// 移除封面图
const handleCoverRemove = () => {
  coverFile.value = null
  coverUrl.value = ''
  goodsModel.value.goodsPic = ''
}

// 详情图上传成功回调
const detailUploadSuccess = (response, uploadFile) => {
  const url = response.data.url
  uploadFile.url = url
  detailUrlList.value.push(url)
  // 构建GoodsImage对象，适配后端DTO
  goodsModel.value.imageList.push({
    goodsId: goodsModel.value.id || 0,
    imageUrl: url,
    sort: detailUrlList.value.length // 排序值
  })
  ElMessage.success('详情图上传成功')
}

// 移除详情图
const handleDetailRemove = (uploadFile) => {
  const index = detailUrlList.value.indexOf(uploadFile.url)
  if (index !== -1) {
    detailUrlList.value.splice(index, 1)
    goodsModel.value.imageList.splice(index, 1)
  }
}

// 图片上传失败回调
const uploadError = () => {
  ElMessage.error('图片上传失败，请重试')
}

// 提交商品
const submitGoods = async () => {
  if (!goodsModel.value.goodsName) return ElMessage.warning('商品名称不能为空')
  if (!goodsModel.value.categoryId) return ElMessage.warning('请选择商品分类')
  if (goodsModel.value.originalPrice < 0) return ElMessage.warning('原价不能为负数')
  if (!goodsModel.value.sellPrice || goodsModel.value.sellPrice <= 0) return ElMessage.warning('售卖价格必须大于0')
  if (goodsModel.value.isNew === null || goodsModel.value.isNew === undefined) return ElMessage.warning('请选择新旧程度')
  if (!goodsModel.value.sellerId) return ElMessage.warning('卖家ID不能为空，请先登录')
  if (goodsModel.value.stock < 1 || !goodsModel.value.goodsDesc) return ElMessage.warning('请完善库存和商品描述信息')
  if (!goodsModel.value.goodsPic) return ElMessage.warning('请上传商品封面图')

  try {
    const submitData = {
      ...goodsModel.value,
      originalPrice: Number(goodsModel.value.originalPrice),
      sellPrice: Number(goodsModel.value.sellPrice),
      categoryId: Number(goodsModel.value.categoryId),
      isNew: Number(goodsModel.value.isNew),
      sellerId: Number(goodsModel.value.sellerId),
      stock: Number(goodsModel.value.stock)
    }
    await goodsAddService(submitData)
    ElMessage.success('商品发布成功')
    await router.push('/homes/mygoods')
  } catch (error) {
    ElMessage.error('发布失败：' + (error.message || '服务器错误'))
  }
}

onMounted(() => {
  getCategoryList()
})
</script>

<template>
  <div class="goods-publish-container">
    <div class="main-wrapper">
      <div class="main-layout">
        <div class="left-panel">
          <div class="form-block">
            <div class="block-title">商品名</div>
            <el-input
                v-model="goodsModel.goodsName"
                placeholder="请输入商品名称"
                class="goods-name-input"
            />
          </div>

          <div class="form-block">
            <div class="block-title">新旧程度</div>
            <!-- 替换为下拉选择器，更友好且避免输入错误 -->
            <el-select
                v-model="goodsModel.isNew"
                placeholder="请选择商品新旧程度"
                class="new-degree-select"
            >
              <el-option
                  v-for="item in newDegreeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
              />
            </el-select>
            <!-- 移除错误的数字提示，下拉选择器已直观展示 -->
          </div>

          <div class="form-block">
            <div class="block-title">价格</div>
            <div class="price-group">
              <div class="price-item">
                <label class="price-label">售卖价格</label>
                <el-input-number
                    v-model="goodsModel.sellPrice"
                    :min="0.01"
                    :step="0.01"
                    :controls="false"
                    placeholder="请输入售卖价格"
                    class="price-input"
                />
              </div>
              <div class="price-item">
                <label class="price-label">商品原价</label>
                <el-input-number
                    v-model="goodsModel.originalPrice"
                    :min="0"
                    :step="0.01"
                    :controls="false"
                    placeholder="请输入商品原价"
                    class="original-price-input"
                />
              </div>
            </div>
          </div>

          <div class="form-block">
            <el-button
                type="primary"
                class="publish-btn"
                @click="submitGoods"
            >
              发布商品
            </el-button>
          </div>
        </div>

        <div class="right-panel">

          <div class="form-block">
            <div class="block-title">产品图</div>

            <div class="images-inline-container">
              <div class="img-group">
                <div class="section-title">封面图</div>
                <div class="cover-img-box" @click="coverInput.click()">
                  <img
                      v-if="coverUrl"
                      :src="coverUrl"
                      alt="封面图"
                      class="cover-img"
                  >
                  <div v-else class="cover-placeholder">
                    <el-icon><Plus /></el-icon>
                  </div>
                  <input
                      type="file"
                      accept="image/*"
                      class="cover-file-input"
                      @change="handleCoverChange($event.target.files[0])"
                  >
                  <el-icon
                      v-if="coverUrl"
                      class="cover-remove-icon"
                      @click.stop="handleCoverRemove"
                  >
                    <Close />
                  </el-icon>
                </div>
              </div>

              <div class="img-group detail-group">
                <div class="section-title">详情图 (最多5张)</div>
                <el-upload
                    v-model:file-list="detailFileList"
                    class="detail-img-upload"
                    action="http://localhost:8080/upload"
                    name="file"
                    :headers="{ Authorization: tokenStore.token }"
                    list-type="picture-card"
                    :on-success="detailUploadSuccess"
                    :on-remove="handleDetailRemove"
                    :on-error="uploadError"
                    multiple
                    :limit="5"
                >
                  <div class="detail-placeholder">
                    <el-icon><Plus /></el-icon>
                  </div>
                </el-upload>
              </div>
            </div>
          </div>

          <div class="form-block">
            <div class="block-title">所属商品类别</div>
            <div class="category-tags-container">
              <div
                  v-for="c in categorys"
                  :key="c.id"
                  class="tag-item"
                  :class="{ 'active': goodsModel.categoryId === c.id }"
                  @click="goodsModel.categoryId = c.id"
              >
                {{ c.categoryName }}
              </div>
            </div>
          </div>

          <div class="form-block">
            <div class="block-title">库存</div>
            <el-input-number
                v-model="goodsModel.stock"
                :min="1"
                :step="1"
                class="stock-input"
            />
          </div>

          <div class="form-block editor-block">
            <div class="block-title">商品描述</div>
            <div class="editor-container">
              <QuillEditor
                  v-model="goodsModel.goodsDesc"
                  previewTheme="vuepress"
                  codeTheme="atom"
                  placeholder="请输入商品详细描述..."
                  :height="350"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// 全局容器：限制最大宽度，添加内边距，防止溢出
.goods-publish-container {
  padding: 80px 16px;
  background-color: #fff;
  min-height: 100vh;
  box-sizing: border-box; // 内边距计入宽度，防止溢出
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.05);  //边框阴影
}

// 主包装器：限制最大宽度，居中显示
.main-wrapper {
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

// 主布局：响应式适配，小屏幕自动堆叠
.main-layout {
  display: flex;
  gap: 24px;
  width: 100%;
  flex-wrap: wrap; // 小屏幕自动换行
  box-sizing: border-box;

  .left-panel {
    flex: 0 0 280px; // 固定宽度，不伸缩
    min-width: 280px; // 最小宽度，防止被压缩
    box-sizing: border-box;
  }

  .right-panel {
    flex: 1; // 自适应剩余宽度
    min-width: 300px; // 最小宽度，保证可用性
    box-sizing: border-box;
  }

  // 响应式：屏幕小于768px时，左右面板堆叠
  @media (max-width: 768px) {
    flex-direction: column;
    .left-panel, .right-panel {
      width: 100%;
      flex: none;
    }
  }
}

/* 通用区块标题 */
.block-title {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  margin-bottom: 12px;
}

.form-block {
  margin-bottom: 24px;
  width: 100%;
  box-sizing: border-box;
}

/* 左侧面板样式 */
.goods-name-input {
  width: 100%;
  font-size: 16px;
  box-sizing: border-box;

  :deep(.el-input__wrapper) {
    border-radius: 4px;
    width: 100%;
    box-sizing: border-box;
  }
}

// 新增：新旧程度下拉选择器样式
.new-degree-select {
  width: 100%;
  box-sizing: border-box;

  :deep(.el-select__wrapper) {
    border-radius: 4px;
    width: 100%;
    box-sizing: border-box;
  }
}

/* 价格组：原价 + 现价 */
.price-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;

  .price-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
    box-sizing: border-box;
  }

  .price-label {
    font-size: 13px;
    color: #6b7280;
    font-weight: 500;
  }

  :deep(.price-input) {
    width: 100%;
    box-sizing: border-box;

    .el-input__inner {
      font-size: 28px;
      font-weight: 600;
      color: #111827;
      text-align: left;
    }
  }

  :deep(.original-price-input) {
    width: 100%;
    box-sizing: border-box;

    .el-input__inner {
      font-size: 16px;
      text-align: left;
      text-decoration: line-through;
      color: #9ca3af;
    }
  }
}

.publish-btn {
  width: 100%;
  height: 40px;
  background-color: #303133;
  border-color: #303133;
  border-radius: 4px;
  box-sizing: border-box;

  &:hover {
    background-color: #444547;
    border-color: #444547;
  }
}

/* 右侧面板 - 产品图同行排版样式 */
.images-inline-container {
  display: flex;
  flex-wrap: wrap; // 允许同行超宽时整体换行
  gap: 24px; // 封面和详情图模块之间的间距
  align-items: flex-start;
  width: 100%;
  box-sizing: border-box;
}

.img-group {
  display: flex;
  flex-direction: column;
  gap: 8px; // 标题与图表框间距
  max-width: 100%;
}

.detail-group {
  flex: 1; // 占满剩余宽度，防止详情图挤压溢出
  min-width: 0;
}

.section-title {
  font-size: 13px;
  color: #666;
}

/* 封面图上传框：核心样式 */
.cover-img-box {
  width: 120px;
  height: 120px;
  border: 1px dashed #e5e5e5;
  border-radius: 4px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  box-sizing: border-box;
  flex-shrink: 0;

  &:hover {
    border-color: #409eff;
  }
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
  font-size: 24px;
}

.cover-file-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  z-index: 1;
}

.cover-remove-icon {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 20px;
  height: 20px;
  background-color: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  z-index: 2;
  cursor: pointer;

  &:hover {
    background-color: #f56c6c;
  }
}

/* 详情图上传：内部自动换行防溢出 */
.detail-img-upload {
  display: flex;
  flex-wrap: wrap; // 允许图片超出时换行
  gap: 10px;
  width: 100%;
  box-sizing: border-box;

  :deep(.el-upload--picture-card) {
    width: 120px;
    height: 120px;
    border-radius: 4px;
    margin: 0;
    flex-shrink: 0; // 防止被压缩
  }

  :deep(.el-upload-list) {
    display: flex;
    gap: 10px;
    flex-wrap: wrap; // 核心：多张图片自动换行且不溢出
    width: 100%;
    margin: 0;

    .el-upload-list__item {
      width: 120px;
      height: 120px;
      margin: 0;
      border-radius: 4px;
      flex-shrink: 0; // 防止被压缩
    }
  }
}

.detail-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
  font-size: 24px;
}

/* 分类标签容器 */
.category-tags-container {
  display: flex;
  gap: 8px;
  flex-wrap: wrap; // 自动换行
  width: 100%;
  box-sizing: border-box;
}

.tag-item {
  padding: 6px 16px;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  flex-shrink: 0; // 防止被压缩

  &.active {
    background-color: #409eff;
    color: #fff;
    border-color: #409eff;
  }
}

/* 库存输入 */
.stock-input {
  width: 200px;
  max-width: 100%; // 不超过容器宽度
  box-sizing: border-box;

  :deep(.el-input-number__wrapper) {
    border-radius: 4px;
    width: 100%;
    box-sizing: border-box;
  }
}

/* 富文本编辑器容器 */
.editor-container {
  width: 100%;
  max-width: 100%; // 不超过容器宽度
  box-sizing: border-box;
}

.editor-block {
  margin-top: 30px;
  width: 100%;
  box-sizing: border-box;

  :deep(.ql-toolbar) {
    border-radius: 4px 4px 0 0;
    border: 1px solid #e5e5e5;
    width: 100%;
    box-sizing: border-box;
  }

  :deep(.ql-container) {
    border-radius: 0 0 4px 4px;
    border: 1px solid #e5e5e5;
    border-top: none;
    width: 100%;
    box-sizing: border-box;
  }
}
</style>