<script setup lang="ts">
import { NResult, NButton } from 'naive-ui'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div style="padding: 60px">
    <n-result status="404" title="404" description="页面不存在">
      <template #footer>
        <n-button type="primary" @click="router.push('/')">返回首页</n-button>
      </template>
    </n-result>
  </div>
</template>
