export function requireUnlinkedAuthEmailAvailable(user) {
  if (user?.id) throw new Error("auth_user_email_collision");
}
