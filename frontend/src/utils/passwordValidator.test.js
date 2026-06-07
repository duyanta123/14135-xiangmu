/**
 * 密码校验工具 — 全面测试套件
 * 覆盖所有密码校验场景，确保实时校验规则准确、及时
 */
import { describe, it, expect } from 'vitest'
import { getPasswordValidationError, getConfirmPasswordError } from './passwordValidator.js'

describe('getPasswordValidationError — 密码复杂度校验', () => {

  // ==================== 空值校验 ====================
  describe('空值校验', () => {
    it('空字符串应返回"密码不能为空"', () => {
      expect(getPasswordValidationError('')).toBe('密码不能为空')
    })
    it('null 应返回"密码不能为空"', () => {
      expect(getPasswordValidationError(null)).toBe('密码不能为空')
    })
    it('undefined 应返回"密码不能为空"', () => {
      expect(getPasswordValidationError(undefined)).toBe('密码不能为空')
    })
  })

  // ==================== 空格校验 ====================
  describe('空格校验', () => {
    it('包含前导空格', () => {
      expect(getPasswordValidationError(' Abc12345')).toBe('密码中不能包含空格')
    })
    it('包含尾部空格', () => {
      expect(getPasswordValidationError('Abc12345 ')).toBe('密码中不能包含空格')
    })
    it('包含中间空格', () => {
      expect(getPasswordValidationError('Abc 12345')).toBe('密码中不能包含空格')
    })
    it('仅包含空格', () => {
      expect(getPasswordValidationError('        ')).toBe('密码中不能包含空格')
    })
  })

  // ==================== 长度校验 ====================
  describe('长度校验 — 过短', () => {
    it('1个字符', () => {
      expect(getPasswordValidationError('a')).toBe('密码长度不足，至少需要8个字符')
    })
    it('4个字符', () => {
      expect(getPasswordValidationError('Abc1')).toBe('密码长度不足，至少需要8个字符')
    })
    it('7个字符（边界值）', () => {
      expect(getPasswordValidationError('Abc1234')).toBe('密码长度不足，至少需要8个字符')
    })
  })

  describe('长度校验 — 过长', () => {
    it('21个字符（边界值）', () => {
      expect(getPasswordValidationError('Abc123456789012345678')).toBe('密码长度过长，最多20个字符')
    })
    it('50个字符', () => {
      expect(getPasswordValidationError('A'.repeat(50) + 'b1!')).toBe('密码长度过长，最多20个字符')
    })
  })

  describe('长度校验 — 合法范围', () => {
    it('8个字符（下边界）', () => {
      expect(getPasswordValidationError('Abc1234!')).toBeNull()
    })
    it('20个字符（上边界）', () => {
      expect(getPasswordValidationError('Abc123456789012345!')).toBeNull()
    })
    it('12个字符（中间值）', () => {
      expect(getPasswordValidationError('MyPass@2024!')).toBeNull()
    })
  })

  // ==================== 复杂度校验 ====================
  describe('复杂度校验 — 仅包含一种类型', () => {
    it('纯小写字母', () => {
      const err = getPasswordValidationError('abcdefghij')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('大写字母')
      expect(err).toContain('数字')
      expect(err).toContain('特殊符号')
    })
    it('纯大写字母', () => {
      const err = getPasswordValidationError('ABCDEFGHIJ')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('小写字母')
      expect(err).toContain('数字')
      expect(err).toContain('特殊符号')
    })
    it('纯数字', () => {
      const err = getPasswordValidationError('1234567890')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('小写字母')
      expect(err).toContain('大写字母')
      expect(err).toContain('特殊符号')
    })
    it('纯特殊符号', () => {
      const err = getPasswordValidationError('!@#$%^&*()_')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('小写字母')
      expect(err).toContain('大写字母')
      expect(err).toContain('数字')
    })
  })

  describe('复杂度校验 — 仅包含两种类型', () => {
    it('小写+数字，缺少大写和特殊符号', () => {
      const err = getPasswordValidationError('abcdefg123')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('大写字母')
      expect(err).toContain('特殊符号')
    })
    it('大写+数字，缺少小写和特殊符号', () => {
      const err = getPasswordValidationError('ABCDEFG123')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('小写字母')
      expect(err).toContain('特殊符号')
    })
    it('小写+特殊符号，缺少大写和数字', () => {
      const err = getPasswordValidationError('abcdefg!@#')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('大写字母')
      expect(err).toContain('数字')
    })
    it('大写+小写，缺少数字和特殊符号', () => {
      const err = getPasswordValidationError('Abcdefghij')
      expect(err).toContain('密码复杂度不足')
      expect(err).toContain('数字')
      expect(err).toContain('特殊符号')
    })
  })

  // ==================== 有效密码（包含三种及以上类型） ====================
  describe('有效密码 — 包含三种类型', () => {
    it('小写+大写+数字', () => {
      expect(getPasswordValidationError('Abcdefg123')).toBeNull()
    })
    it('小写+大写+特殊符号', () => {
      expect(getPasswordValidationError('Abcdefg!@#')).toBeNull()
    })
    it('小写+数字+特殊符号', () => {
      expect(getPasswordValidationError('abcdefg123!')).toBeNull()
    })
    it('大写+数字+特殊符号', () => {
      expect(getPasswordValidationError('ABCDEFG123!')).toBeNull()
    })
  })

  describe('有效密码 — 包含四种类型', () => {
    it('小写+大写+数字+特殊符号', () => {
      expect(getPasswordValidationError('Abcdefg123!')).toBeNull()
    })
    it('复杂密码', () => {
      expect(getPasswordValidationError('P@ssw0rd2024!')).toBeNull()
    })
    it('中文密码（特殊字符类别）', () => {
      expect(getPasswordValidationError('密码Abc123!测试')).toBeNull()
    })
  })

  // ==================== 边界与特殊场景 ====================
  describe('边界与特殊场景', () => {
    it('Unicode 字符（不属于 ASCII 特殊符号但属于非字母数字）', () => {
      expect(getPasswordValidationError('Abc123你好世界')).toBeNull()
    })
    it('包含下划线', () => {
      expect(getPasswordValidationError('My_Pass_123')).toBeNull()
    })
    it('包含连字符', () => {
      expect(getPasswordValidationError('My-Pass-123')).toBeNull()
    })
    it('恰好8位含三种类型', () => {
      expect(getPasswordValidationError('Abcde!12')).toBeNull()
    })
    it('恰好20位含三种类型', () => {
      expect(getPasswordValidationError('Abcdefghij12345678!')).toBeNull()
    })
  })
})

describe('getConfirmPasswordError — 确认密码校验', () => {
  const password = 'Abc12345!'

  it('空值应返回"请再次输入新密码"', () => {
    expect(getConfirmPasswordError('', password)).toBe('请再次输入新密码')
  })
  it('不一致应返回"两次输入的密码不一致"', () => {
    expect(getConfirmPasswordError('Different123!', password)).toBe('两次输入的密码不一致')
  })
  it('大小写不同', () => {
    expect(getConfirmPasswordError('abc12345!', password)).toBe('两次输入的密码不一致')
  })
  it('一致应返回 null', () => {
    expect(getConfirmPasswordError(password, password)).toBeNull()
  })
})