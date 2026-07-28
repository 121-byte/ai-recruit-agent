import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { compareResumes } from '@/api'

/**
 * 简历对比组合式函数（§4）
 * 调用 POST /api/resumes/compare（body { resumeIds }）对比多份简历。
 */
export function useCompareTask() {
  const loading = ref(false)
  const result = ref(null)
  const error = ref(null)

  async function compare(resumeIds) {
    if (!Array.isArray(resumeIds) || resumeIds.length === 0) {
      error.value = new Error('resumeIds 不能为空')
      message.error('请选择至少两份简历')
      return null
    }
    loading.value = true
    error.value = null
    try {
      const data = await compareResumes(resumeIds)
      result.value = data
      return data
    } catch (e) {
      error.value = e
      message.error(e.message || '简历对比失败')
      return null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    loading.value = false
    result.value = null
    error.value = null
  }

  return {
    loading,
    result,
    error,
    compare,
    reset
  }
}

export default useCompareTask
