# ADR-0001：采用 Kotlin + Jetpack Compose 与 Cupertino 风格

- 状态：已接受
- 日期：2026-09-09

## 背景

应用只服务单一 Android 用户，需要快速迭代练习、历史和设置界面，并要求视觉上接近 iOS。

## 决策

使用 Kotlin、Jetpack Compose 和 Android 原生能力；建立独立的 Cupertino 风格设计令牌（圆角、分组列表、细分隔线、柔和背景、底部标签栏），而不是引入完整的 iOS 组件移植层。最低支持 Android 12，仅适配竖屏手机。

## 后果

界面状态可直接由 Compose 状态驱动，图片选择、相机和系统返回行为使用 Android 平台接口。视觉规范由项目维护，不能把 Material 默认外观当作产品风格；无障碍、字体缩放、深浅色仍使用 Compose/系统能力验证。

