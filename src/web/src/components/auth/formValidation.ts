const PASSWORD_MIN = 15
const PASSWORD_MAX = 128

export const passwordLengthHint = '使用 15–128 个字符，可粘贴密码或使用密码管理器。'

type PasswordIssue = { field: 'password' | 'confirmation'; message: string }

export function validateNewPassword(password: string, confirmation: string): PasswordIssue | null {
  const normalizedPassword = password.normalize('NFC')
  const length = [...normalizedPassword].length

  if (length < PASSWORD_MIN || length > PASSWORD_MAX) {
    return { field: 'password', message: '密码长度需为 15–128 个字符。' }
  }

  if (normalizedPassword !== confirmation.normalize('NFC')) {
    return { field: 'confirmation', message: '两次输入的密码不一致。' }
  }

  return null
}
