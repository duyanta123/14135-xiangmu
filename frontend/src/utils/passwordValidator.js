/**
 * 密码校验工具模块
 * 提供密码复杂度校验、确认密码校验和校验日志功能
 */

/**
 * 检测密码复杂度等级（包含大小写字母、数字、特殊符号中的几种）
 * @param {string} password
 * @returns {number} 0-4
 */
function getComplexity(password) {
  let complexity = 0
  if (/[a-z]/.test(password)) complexity++
  if (/[A-Z]/.test(password)) complexity++
  if (/[0-9]/.test(password)) complexity++
  if (/[^a-zA-Z0-9]/.test(password)) complexity++
  return complexity
}

/**
 * 获取密码校验失败原因（纯函数，方便测试）
 * @param {string} password - 待校验的密码
 * @returns {string|null} 失败原因，通过返回 null
 */
export function getPasswordValidationError(password) {
  if (!password) {
    return '密码不能为空'
  }
  if (password.includes(' ')) {
    return '密码中不能包含空格'
  }
  if (password.length < 8) {
    return '密码长度不足，至少需要8个字符'
  }
  if (password.length > 20) {
    return '密码长度过长，最多20个字符'
  }
  const complexity = getComplexity(password)
  if (complexity < 3) {
    const missing = []
    if (!/[a-z]/.test(password)) missing.push('小写字母')
    if (!/[A-Z]/.test(password)) missing.push('大写字母')
    if (!/[0-9]/.test(password)) missing.push('数字')
    if (!/[^a-zA-Z0-9]/.test(password)) missing.push('特殊符号')
    return `密码复杂度不足，缺少：${missing.join('、')}（需包含至少三种）`
  }
  return null
}

/**
 * 获取确认密码校验失败原因
 * @param {string} confirmPassword
 * @param {string} password
 * @returns {string|null}
 */
export function getConfirmPasswordError(confirmPassword, password) {
  if (!confirmPassword) {
    return '请再次输入新密码'
  }
  if (confirmPassword !== password) {
    return '两次输入的密码不一致'
  }
  return null
}

/**
 * Element Plus 表单校验器：密码规则
 */
export function validatePassword(_rule, value, callback) {
  const error = getPasswordValidationError(value)
  if (error) {
    callback(new Error(error))
    return
  }
  callback()
}

/**
 * Element Plus 表单校验器：确认密码规则
 */
export function validateConfirmPassword(_rule, value, callback, password) {
  const error = getConfirmPasswordError(value, password)
  if (error) {
    callback(new Error(error))
    return
  }
  callback()
}

/**
 * 密码校验日志记录
 * @param {'student'|'teacher'} targetType - 目标类型
 * @param {string} targetNo - 学号/工号
 * @param {string} targetName - 姓名
 * @param {string} password - 输入的密码
 * @param {boolean} passed - 校验是否通过
 * @param {string|null} reason - 失败原因（通过时为 null）
 */
export function logPasswordValidation(targetType, targetNo, targetName, password, passed, reason) {
  const timestamp = new Date().toISOString()
  const logEntry = {
    timestamp,
    targetType,
    targetNo,
    targetName,
    passwordLength: password.length,
    complexity: getComplexity(password),
    passed,
    reason: reason || null
  }
  if (passed) {
    console.log(
      `[密码校验] ${timestamp} | ${targetType === 'student' ? '学生' : '教师'}: ${targetNo}(${targetName}) | ` +
      `结果: 通过 | 长度: ${password.length} | 复杂度: ${logEntry.complexity}/4`
    )
  } else {
    console.warn(
      `[密码校验] ${timestamp} | ${targetType === 'student' ? '学生' : '教师'}: ${targetNo}(${targetName}) | ` +
      `结果: 失败 | 原因: ${reason}`
    )
  }
  return logEntry
}

/**
 * 密码校验规则配置（供 el-form :rules 使用）
 * @param {import('vue').Ref<string>} passwordRef - 密码的响应式引用
 * @returns {object} rules 对象
 */
export function createPasswordRules(passwordRef) {
  return {
    password: [
      { validator: validatePassword, trigger: ['blur', 'change'] }
    ],
    confirmPassword: [
      {
        validator: (_rule, value, callback) => {
          validateConfirmPassword(_rule, value, callback, passwordRef.value)
        },
        trigger: ['blur', 'change']
      }
    ]
  }
}