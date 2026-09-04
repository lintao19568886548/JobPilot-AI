<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { globalSearch } from '../api/search.js'
import { displayText } from '../utils/displayText.js'

const router = useRouter()
const open = ref(false)
const query = ref('')
const loading = ref(false)
const result = ref(null)
const error = ref('')
let timer

watch(query, (value) => {
  window.clearTimeout(timer)
  error.value = ''
  if (value.trim().length < 2) {
    result.value = null
    return
  }
  timer = window.setTimeout(search, 250)
})

onMounted(() => window.addEventListener('keydown', shortcut))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', shortcut)
  window.clearTimeout(timer)
})

function shortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    open.value = true
  }
  if (event.key === 'Escape') open.value = false
}

async function search() {
  loading.value = true
  try {
    const { data } = await globalSearch(query.value.trim())
    result.value = data.data
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '搜索失败'
  } finally {
    loading.value = false
  }
}

async function navigate(item) {
  open.value = false
  await router.push(item.targetUrl)
}
</script>

<template>
  <div class="global-search">
    <button class="search-trigger" @click="open = true"><span>⌕</span> 搜索岗位、公司、技能 <kbd>Ctrl K</kbd></button>
    <el-dialog v-model="open" class="search-dialog" width="680px" :show-close="false" append-to-body>
      <div class="search-input-row"><span>⌕</span><input v-model="query" autofocus maxlength="100" placeholder="输入至少 2 个字符" /><kbd>退出</kbd></div>
      <div v-loading="loading" class="search-results">
        <p v-if="error" class="search-error">{{ error }}</p>
        <template v-else-if="result">
          <section v-for="group in result.groups" :key="group.type">
            <header><strong>{{ displayText(group.type) }}</strong><span>{{ group.items.length }}</span></header>
            <button v-for="item in group.items" :key="`${item.type}-${item.id}`" @click="navigate(item)"><div><strong>{{ item.title }}</strong><span>{{ item.subtitle }}</span></div><small>{{ displayText(item.status) }}</small></button>
          </section>
          <div v-if="!result.total" class="empty-state compact">没有匹配的真实数据。</div>
        </template>
        <div v-else class="search-hint">支持按岗位、公司、技能和状态搜索；结果仅包含当前用户的数据。</div>
      </div>
    </el-dialog>
  </div>
</template>
