<script setup lang="ts">
defineProps<{
  title: string
  description: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="page-header">
    <div>
      <div v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="page-header__actions">
      <slot />
    </div>
  </header>
</template>

