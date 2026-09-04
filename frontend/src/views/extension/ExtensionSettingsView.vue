<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from '../../utils/elementFeedback.js'
import { createExtensionPairingCode, listExtensionDevices, revokeExtensionDevice } from '../../api/extension.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'

const loading = ref(true)
const working = ref(false)
const pairing = ref(null)
const devices = ref([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await listExtensionDevices()
    devices.value = data.data || []
  } catch (error) { ElMessage.error(apiErrorMessage(error, '扩展设备加载失败')) }
  finally { loading.value = false }
}

async function createCode() {
  working.value = true
  try {
    const { data } = await createExtensionPairingCode()
    pairing.value = data.data
    ElMessage.success('一次性配对码已创建')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '配对码创建失败')) }
  finally { working.value = false }
}

async function copyCode() {
  if (!pairing.value?.pairingCode) return
  await navigator.clipboard.writeText(pairing.value.pairingCode)
  ElMessage.success('配对码已复制')
}

async function revoke(device) {
  working.value = true
  try {
    await revokeExtensionDevice(device.id)
    await load()
    ElMessage.success('设备授权已撤销，现有 Access/Refresh Token 将失效')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '撤销失败')) }
  finally { working.value = false }
}

function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
</script>

<template>
  <section v-loading="loading" class="page-section extension-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow accent">阶段 7 · 受控辅助</p>
        <h1>浏览器扩展</h1>
        <p>连接 JobPilot AI 申请助手，保存当前可见岗位并准备人工审批的申请材料。</p>
      </div>
      <el-button type="primary" :loading="working" @click="createCode">创建一次性配对码</el-button>
    </div>

    <div class="safety-strip">
      <strong>辅助安全模式</strong>
      <span>仅读取用户主动触发时的可见字段；不读取 Cookie、密码、验证码和隐藏字段；不会自动发送消息或提交申请。</span>
    </div>

    <div class="extension-grid">
      <article class="surface pairing-area">
        <header><span>设备配对</span><strong>一次性配对</strong></header>
        <template v-if="pairing">
          <div class="pairing-code">{{ pairing.pairingCode }}</div>
          <p>有效期至 {{ formatTime(pairing.expiresAt) }}。成功使用一次后立即失效。</p>
          <div class="scope-list"><span v-for="scope in pairing.scopes" :key="scope">{{ scope }}</span></div>
          <el-button @click="copyCode">复制配对码</el-button>
        </template>
        <div v-else class="pairing-placeholder">
          <span>01</span>
          <p>点击右上角创建配对码，然后在 Chrome 侧栏中输入。配对码不会替代网站登录，也不会接触浏览器 Cookie。</p>
        </div>
      </article>

      <article class="surface install-area">
        <header><span>本地安装</span><strong>加载开发版扩展</strong></header>
        <ol>
          <li><span>01</span><p>运行 <code>npm run build</code> 构建 <code>extension/dist</code>。</p></li>
          <li><span>02</span><p>在 Chrome 扩展管理页启用开发者模式。</p></li>
          <li><span>03</span><p>选择“加载已解压的扩展程序”，定位到 <code>D:\JobPilot AI\extension\dist</code>。</p></li>
          <li><span>04</span><p>打开任意 HTTP(S) 岗位页，点击 JobPilot 图标后主动提取。</p></li>
        </ol>
      </article>
    </div>

    <article class="surface device-area">
      <header><div><span>授权设备</span><h2>已授权设备</h2></div><strong>{{ devices.length }}</strong></header>
      <div v-if="!devices.length" class="empty-state compact">尚无已配对设备。</div>
      <div v-else class="device-list">
        <div v-for="device in devices" :key="device.id" class="device-row">
          <div class="device-mark">扩</div>
          <div class="device-main"><strong>{{ displayText(device.deviceName) }}</strong><span>{{ displayText(device.browserName) }} · 扩展版本 {{ device.extensionVersion }}</span></div>
          <div class="device-time"><span>最近活动</span><strong>{{ formatTime(device.lastSeenAt || device.pairedAt) }}</strong></div>
          <span class="device-status" :class="device.status.toLowerCase()">{{ displayText(device.status) }}</span>
          <el-popconfirm v-if="device.status === 'ACTIVE'" title="撤销后此设备需重新配对，确认继续？" @confirm="revoke(device)">
            <template #reference><el-button text type="danger" :disabled="working">撤销</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.safety-strip { display: grid; grid-template-columns: 160px 1fr; gap: 24px; margin-bottom: 22px; padding: 14px 18px; border: 1px solid #cfe0d6; background: #f4faf6; color: #4d6255; font-size: 12px; line-height: 1.55; }
.safety-strip strong { color: #24563b; }
.extension-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); gap: 18px; margin-bottom: 18px; }
.surface { border: 1px solid var(--line); background: #fff; }
.pairing-area, .install-area { min-height: 290px; padding: 24px; }
article > header { display: flex; justify-content: space-between; margin-bottom: 26px; }
article > header span, .device-area header > div > span { color: var(--muted); font-size: 9px; letter-spacing: .1em; }
article > header > strong { font-size: 13px; }
.pairing-code { margin: 9px 0 12px; color: #244f39; font: 680 29px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .08em; }
.pairing-area p { max-width: 520px; color: var(--muted); font-size: 12px; line-height: 1.65; }
.scope-list { display: flex; flex-wrap: wrap; gap: 5px; margin: 18px 0; }
.scope-list span { padding: 4px 6px; border: 1px solid var(--line); color: #637069; font-size: 9px; }
.pairing-placeholder { display: grid; grid-template-columns: 46px 1fr; align-items: start; color: var(--muted); }
.pairing-placeholder > span { color: #3c7456; font: 700 27px/1 ui-monospace, monospace; }
.pairing-placeholder p { margin: 0; }
.install-area ol { display: grid; gap: 17px; margin: 0; padding: 0; list-style: none; }
.install-area li { display: grid; grid-template-columns: 30px 1fr; gap: 10px; border-bottom: 1px solid #edf0ec; padding-bottom: 14px; }
.install-area li:last-child { border-bottom: 0; }
.install-area li > span { color: #3c7456; font: 700 10px/1.6 ui-monospace, monospace; }
.install-area p { margin: 0; color: #56615a; font-size: 12px; line-height: 1.55; }
code { color: #315c46; font: 11px ui-monospace, monospace; }
.device-area { padding: 23px 24px; }
.device-area header { align-items: flex-end; margin-bottom: 12px; }
.device-area h2 { margin: 5px 0 0; font-size: 18px; }
.device-area header > strong { color: #315c46; font-size: 25px; }
.device-row { display: grid; grid-template-columns: 38px minmax(180px, 1fr) minmax(150px, .7fr) 80px 70px; gap: 14px; align-items: center; min-height: 68px; border-top: 1px solid #e8ebe7; }
.device-mark { display: grid; width: 31px; height: 31px; place-items: center; border: 1px solid #cdd5cf; color: #315c46; font: 700 9px ui-monospace, monospace; }
.device-main, .device-time { display: grid; gap: 4px; }
.device-main strong, .device-time strong { font-size: 12px; }
.device-main span, .device-time span { color: var(--muted); font-size: 10px; }
.device-status { color: #17633b; font-size: 10px; font-weight: 700; }
.device-status.revoked { color: #9c4545; }
@media (max-width: 900px) { .extension-grid { grid-template-columns: 1fr; }.device-row { grid-template-columns: 38px 1fr auto; }.device-time { display: none; } }
</style>
