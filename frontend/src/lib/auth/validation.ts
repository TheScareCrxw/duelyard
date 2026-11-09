/**
 * Form validation shared by the auth server actions. Username rules mirror the backend
 * (UsernamePolicy + DB constraint); password rules mirror the Cognito user pool policy.
 */
export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/;
export const PASSWORD_MIN_LENGTH = 8;

export type FieldErrors = Partial<Record<string, string>>;

export function validateUsername(username: string): string | undefined {
  if (!username) return "Choose a username.";
  if (!USERNAME_PATTERN.test(username)) return "3–20 characters: letters, numbers and underscores only.";
  return undefined;
}

export function validateEmail(email: string): string | undefined {
  if (!email) return "Enter your email address.";
  if (email.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return "Enter a valid email address.";
  return undefined;
}

export function validatePassword(password: string): string | undefined {
  if (password.length < PASSWORD_MIN_LENGTH) return `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
  if (password.length > 256) return "That password is too long.";
  if (!/[a-z]/.test(password) || !/[A-Z]/.test(password)) return "Include both uppercase and lowercase letters.";
  if (!/[0-9]/.test(password)) return "Include at least one number.";
  if (!/[^A-Za-z0-9]/.test(password)) return "Include at least one symbol.";
  return undefined;
}

export function validateCode(code: string): string | undefined {
  if (!/^\d{6}$/.test(code)) return "Enter the 6-digit code from your email.";
  return undefined;
}

export function formString(formData: FormData, key: string): string {
  const value = formData.get(key);
  return typeof value === "string" ? value.trim() : "";
}

/** Passwords are never trimmed: leading/trailing spaces are significant. */
export function formPassword(formData: FormData, key: string): string {
  const value = formData.get(key);
  return typeof value === "string" ? value : "";
}
