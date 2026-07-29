<template>
  <MainLayout>
    <div class="users-toolbar">
      <div class="filter-group">
        <svg width="16" height="16" viewBox="0 0 24 24" style="color:var(--muted)"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        <span class="users-title">系统用户</span>
      </div>
      <button class="btn btn-primary" @click="openCreate">
        <svg width="16" height="16" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建用户
      </button>
    </div>

    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="users"
        row-key="id"
        :pagination="false"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'roles'">
            <a-tag v-for="r in (record.roles || [])" :key="r" :color="roleColor(r)">
              {{ roleMap[r] || r }}
            </a-tag>
            <span v-if="!(record.roles && record.roles.length)" style="color:var(--muted)">—</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'green' : 'red'">
              {{ record.status === 'active' ? '正常' : '已禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ fmtDate(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a @click="openEdit(record)">编辑</a>
              <a :class="{ 'link-disabled': isSelf(record) }" @click="toggleStatus(record)">
                {{ record.status === 'active' ? '禁用' : '启用' }}
              </a>
              <a class="danger-link" :class="{ 'link-disabled': isSelf(record) }" @click="remove(record)">删除</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-spin>

    <a-modal
      v-model:open="showForm"
      :title="editing ? '编辑用户' : '新建用户'"
      :confirm-loading="saving"
      :mask-closable="false"
      @ok="handleSave"
    >
      <a-form layout="vertical">
        <a-form-item label="用户名">
          <a-input v-model:value="form.username" :disabled="!!editing" placeholder="登录用户名" />
        </a-form-item>
        <a-form-item :label="editing ? '密码（留空不改）' : '密码'">
          <a-input-password v-model:value="form.password" :placeholder="editing ? '留空则不修改' : '初始密码，默认 123456'" />
        </a-form-item>
        <a-form-item label="姓名"><a-input v-model:value="form.realName" /></a-form-item>
        <a-form-item label="部门"><a-input v-model:value="form.department" /></a-form-item>
        <a-form-item label="邮箱"><a-input v-model:value="form.email" /></a-form-item>
        <a-form-item label="手机"><a-input v-model:value="form.phone" /></a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="form.roles"
            mode="multiple"
            :options="roleOptions"
            placeholder="选择角色（可多选）"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status">
            <a-select-option value="active">正常</a-select-option>
            <a-select-option value="disabled">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import MainLayout from '@/components/MainLayout.vue'
import { useAuthStore } from '@/store/auth'
import {
  listUsers, listRoles, createUser, updateUser, deleteUser, updateUserRoles
} from '@/api'

const authStore = useAuthStore()

const users = ref([])
const roles = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const editing = ref(null)
const form = reactive({
  username: '', password: '', realName: '', department: '',
  email: '', phone: '', roles: [], status: 'active',
})

const roleMap = { HR: '招聘负责人', OPS: '运营人员', ADMIN: '管理员' }
const roleColor = (r) => ({ HR: 'blue', OPS: 'purple', ADMIN: 'red' }[r] || 'default')
const roleOptions = computed(() =>
  roles.value.map((r) => ({ label: `${r.name}（${r.code}）`, value: r.code }))
)

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'realName', key: 'realName' },
  { title: '部门', dataIndex: 'department', key: 'department' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '手机', dataIndex: 'phone', key: 'phone' },
  { title: '角色', key: 'roles' },
  { title: '状态', key: 'status' },
  { title: '创建时间', key: 'createdAt' },
  { title: '操作', key: 'action', width: 180 },
]

// 当前登录用户自我保护：禁止对自身执行禁用/删除
function isSelf(u) {
  return String(u?.id) === String(authStore.user?.id)
}

function fmtDate(s) {
  if (!s) return '—'
  if (typeof s === 'string') return s.replace('T', ' ').slice(0, 19)
  return String(s)
}

async function load() {
  loading.value = true
  try {
    const [u, r] = await Promise.all([listUsers(), listRoles()])
    users.value = u || []
    roles.value = r || []
  } catch (e) {
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    username: '', password: '', realName: '', department: '',
    email: '', phone: '', roles: [], status: 'active',
  })
}

function openCreate() {
  editing.value = null
  resetForm()
  showForm.value = true
}

function openEdit(u) {
  editing.value = u
  resetForm()
  Object.assign(form, {
    username: u.username || '',
    password: '',
    realName: u.realName || '',
    department: u.department || '',
    email: u.email || '',
    phone: u.phone || '',
    roles: [...(u.roles || [])],
    status: u.status || 'active',
  })
  showForm.value = true
}

async function handleSave() {
  if (!editing.value && !form.username.trim()) {
    message.warning('请填写用户名')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      const id = editing.value.id
      await updateUser(id, {
        realName: form.realName,
        email: form.email,
        phone: form.phone,
        department: form.department,
        status: form.status,
        password: form.password || null,
      })
      await updateUserRoles(id, form.roles)
      message.success('已更新')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName,
        email: form.email,
        phone: form.phone,
        department: form.department,
        status: form.status,
        roles: form.roles,
      })
      message.success('已创建')
    }
    showForm.value = false
    await load()
  } catch (e) {
    message.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(u) {
  if (isSelf(u)) {
    message.warning('不能禁用当前登录账号')
    return
  }
  const status = u.status === 'active' ? 'disabled' : 'active'
  try {
    await updateUser(u.id, { status })
    message.success('已更新状态')
    await load()
  } catch (e) {
    message.error('操作失败')
  }
}

function remove(u) {
  if (isSelf(u)) {
    message.warning('不能删除当前登录账号')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定删除用户「${u.username}」吗？该操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteUser(u.id)
        message.success('已删除')
        await load()
      } catch (e) {
        message.error('删除失败')
      }
    },
  })
}

onMounted(load)
</script>

<style scoped>
.users-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.users-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--fg);
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-soft);
  background: var(--card);
  color: var(--fg);
  padding: 8px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.btn:hover { border-color: var(--accent); color: var(--accent); }
.btn-primary {
  background: var(--accent);
  color: var(--accent-on);
  border-color: var(--accent);
}
.btn-primary:hover { opacity: 0.9; color: var(--accent-on); }
a { cursor: pointer; color: var(--accent); }
a:hover { opacity: 0.8; }
.danger-link { color: var(--danger) !important; }
.link-disabled {
  color: var(--muted) !important;
  cursor: not-allowed;
  pointer-events: none;
  opacity: 0.6;
}
</style>
