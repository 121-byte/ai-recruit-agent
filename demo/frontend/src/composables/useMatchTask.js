import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { runMatch } from '@/api'

/**
 * 匹配任务组合式函数（§4）
 * 调用 POST /api/matches/job/{jobId}/run 触发匹配任务。
 */
export function useMatchTask() {
  const loading = ref(false)
  const result = ref(null)
  const error = ref(null)

  async function run(jobId) {
    if (!jobId) {
      error.value = new Error('缺少 jobId')
      message.error('缺少岗位 ID')
      return null
    }
    loading.value = true
    error.value = null
    try {
      const data = await runMatch(jobId)
      result.value = data
      return data
    } catch (e) {
      error.value = e
      message.error(e.message || '匹配任务失败')
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
    run,
    reset
  }
}

export default useMatchTask
