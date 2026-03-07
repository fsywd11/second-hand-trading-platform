<script setup>
import { ref } from 'vue'
import useUserInfoStore from '@/stores/userInfo.js'
import { userUpdateInfoServices } from '@/api/user.js'
import { ElMessage } from "element-plus";

const userInfoStore = useUserInfoStore();
// 浅拷贝当前 store 信息，新增三个字段的兜底处理
const userInfo = ref({
  ...userInfoStore.info,
  phone: userInfoStore.info.phone || '',
  // ========== 新增字段 ==========
  major: userInfoStore.info.major || '',       // 专业
  grade: userInfoStore.info.grade || '',       // 年级
  campusScene: userInfoStore.info.campusScene || '' // 校园场景
})

// 新增手机号验证规则，同时添加新增字段的校验规则
const rules = {
  nickname: [
    { required: true, message: '请输入用户昵称', trigger: 'blur' },
    { pattern: /^\S{2,10}$/, message: '昵称必须是2-10位的非空字符串', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入用户邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号码', trigger: 'blur' },
    // 中国大陆手机号正则（支持13/14/15/16/17/18/19开头）
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号码', trigger: 'blur' }
  ],
  // ========== 新增字段校验规则 ==========
  major: [
    { required: true, message: '请输入专业', trigger: 'blur' },
    { pattern: /^\S{2,20}$/, message: '专业必须是2-20位的非空字符串', trigger: 'blur' }
  ],
  grade: [
    { required: true, message: '请输入年级', trigger: 'blur' },
    { pattern: /^\S{2,10}$/, message: '年级必须是2-10位的非空字符串', trigger: 'blur' }
  ],
  campusScene: [
    { required: true, message: '请输入校园场景', trigger: 'blur' },
    { pattern: /^\S{2,50}$/, message: '校园场景必须是2-50位的非空字符串', trigger: 'blur' }
  ]
}

// 优化：添加表单校验（避免空数据提交）
const formRef = ref(null)
const updateUserInfoStore = async () => {
  // 先进行表单校验
  try {
    await formRef.value.validate()
  } catch (error) {
    // 校验失败时终止提交
    ElMessage.error('表单填写有误，请检查后重新提交')
    return
  }

  // 调用接口更新后台
  try {
    let result = await userUpdateInfoServices(userInfo.value)
    ElMessage.success(result.data ? result.data : '修改成功')

    // 合并数据，保留原有的 userPic 等其他字段，新增所有字段
    const updatedInfo = {
      ...userInfoStore.info,
      nickname: userInfo.value.nickname,
      email: userInfo.value.email,
      phone: userInfo.value.phone,
      // ========== 新增字段 ==========
      major: userInfo.value.major,
      grade: userInfo.value.grade,
      campusScene: userInfo.value.campusScene
    }

    // 更新 Pinia 状态
    userInfoStore.setInfo(updatedInfo)
  } catch (error) {
    // 接口调用失败时提示
    ElMessage.error('修改失败，请稍后重试')
    console.error('用户信息修改失败：', error)
  }
}
</script>

<template>
  <el-card class="page-container no-select">
    <template #header>
      <div class="header"><span>基本资料</span></div>
    </template>
    <el-row>
      <el-col :span="20">
        <!-- 新增表单引用，用于校验 -->
        <el-form
            ref="formRef"
            :model="userInfo"
            :rules="rules"
            label-width="100px"
            size="large"
        >
          <el-form-item label="登录名称">
            <el-input v-model="userInfo.username" disabled></el-input>
          </el-form-item>
          <el-form-item label="用户昵称" prop="nickname">
            <el-input v-model="userInfo.nickname"></el-input>
          </el-form-item>
          <el-form-item label="用户邮箱" prop="email">
            <el-input v-model="userInfo.email"></el-input>
          </el-form-item>
          <!-- 新增手机号表单项 -->
          <el-form-item label="手机号码" prop="phone">
            <el-input
                v-model="userInfo.phone"
                placeholder="请输入11位手机号码"
                maxlength="11"
                show-word-limit
            ></el-input>
          </el-form-item>
          <!-- ========== 新增字段表单项 ========== -->
          <el-form-item label="专业" prop="major">
            <el-input
                v-model="userInfo.major"
                placeholder="请输入您的专业"
                maxlength="20"
                show-word-limit
            ></el-input>
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input
                v-model="userInfo.grade"
                placeholder="请输入您的年级（如：2023级）"
                maxlength="10"
                show-word-limit
            ></el-input>
          </el-form-item>
          <el-form-item label="校园场景" prop="campusScene">
            <el-input
                v-model="userInfo.campusScene"
                placeholder="请输入校园场景（如：图书馆/教学楼/食堂）"
                maxlength="50"
                show-word-limit
            ></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="updateUserInfoStore">提交修改</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
  </el-card>
</template>

<style scoped>
.page-container { border: none; box-shadow: none; }
/* 可选：优化输入框样式 */
:deep(.el-input__inner) {
  padding: 0 15px;
}
</style>