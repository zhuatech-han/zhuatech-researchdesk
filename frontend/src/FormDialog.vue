<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { reactive, onMounted, onBeforeUnmount, ref } from "vue";
const props = defineProps({
  spec: { type: Object, required: true },
  busy: Boolean,
  error: { type: String, default: "" },
});
const emit = defineEmits(["save", "cancel"]);
const values = reactive(JSON.parse(JSON.stringify(props.spec.values || {})));
const panel = ref(null);
let previousFocus;
function keyboard(event) {
  if (event.key === "Escape" && !props.busy) emit("cancel");
  if (event.key !== "Tab") return;
  const controls = [
    ...panel.value.querySelectorAll(
      "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)",
    ),
  ];
  const first = controls[0],
    last = controls.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
onMounted(() => {
  previousFocus = document.activeElement;
  panel.value
    .querySelector(
      "input:not(:disabled),select:not(:disabled),textarea:not(:disabled),button",
    )
    ?.focus();
  document.addEventListener("keydown", keyboard);
});
onBeforeUnmount(() => {
  document.removeEventListener("keydown", keyboard);
  previousFocus?.focus();
});
function submit() {
  emit("save", values);
}
</script>
<template>
  <div class="modal-mask" @click.self="!busy && emit('cancel')">
    <section
      ref="panel"
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="spec.title"
    >
      <header>
        <h2>{{ spec.title }}</h2>
        <button
          type="button"
          class="icon-button"
          :disabled="busy"
          aria-label="关闭"
          @click="emit('cancel')"
        >
          ×
        </button>
      </header>
      <form @submit.prevent="submit">
        <p v-if="spec.message" class="form-note">{{ spec.message }}</p>
        <div class="fields">
          <label
            v-for="f in spec.fields"
            :key="f.key"
            :class="{
              wide:
                f.type === 'textarea' ||
                f.type === 'checks' ||
                f.type === 'allocations',
            }"
            ><span>{{ f.label }}<em v-if="f.required !== false"> *</em></span
            ><textarea
              v-if="f.type === 'textarea'"
              v-model="values[f.key]"
              :required="f.required !== false"
              :maxlength="f.max || 1600"
              :rows="f.rows || 3"
            ></textarea
            ><select
              v-else-if="f.type === 'select'"
              v-model="values[f.key]"
              :disabled="f.disabled"
              :required="f.required !== false"
            >
              <option value="">请选择</option>
              <option v-for="o in f.options" :key="o.value" :value="o.value">
                {{ o.label }}
              </option>
            </select>
            <div v-else-if="f.type === 'checks'" class="checks">
              <label v-for="o in f.options" :key="o.value"
                ><input
                  v-model="values[f.key]"
                  type="checkbox"
                  :value="o.value"
                />{{ o.label }}</label
              >
            </div>
            <div v-else-if="f.type === 'allocations'" class="allocation-editor">
              <div
                v-for="(line, i) in values[f.key]"
                :key="i"
                class="allocation-line"
              >
                <select v-model="line.fundId" required aria-label="资金来源">
                  <option value="">资金来源</option>
                  <option v-for="o in f.funds" :key="o.value" :value="o.value">
                    {{ o.label }}
                  </option></select
                ><select
                  v-model="line.categoryId"
                  required
                  aria-label="预算科目"
                >
                  <option value="">科目</option>
                  <option
                    v-for="o in f.categories"
                    :key="o.value"
                    :value="o.value"
                  >
                    {{ o.label }}
                  </option></select
                ><input
                  v-model="line.amount"
                  required
                  inputmode="decimal"
                  placeholder="金额（元）"
                  aria-label="分摊金额元"
                /><button
                  type="button"
                  class="secondary"
                  aria-label="删除分摊行"
                  @click="values[f.key].splice(i, 1)"
                >
                  移除
                </button>
              </div>
              <button
                type="button"
                class="secondary"
                :disabled="values[f.key].length >= 30"
                @click="
                  values[f.key].push({ fundId: '', categoryId: '', amount: '' })
                "
              >
                增加分摊
              </button>
            </div>
            <input
              v-else-if="f.type === 'checkbox'"
              v-model="values[f.key]"
              type="checkbox"
              class="check-alone"
            /><input
              v-else-if="f.type === 'file'"
              type="file"
              :accept="f.accept"
              :required="f.required !== false"
              @change="values[f.key] = $event.target.files[0]"
            /><input
              v-else
              v-model="values[f.key]"
              :type="f.type || 'text'"
              :required="f.required !== false"
              :disabled="f.disabled"
              :maxlength="f.max || 160"
              :min="f.min"
              :max="f.maximum"
              :step="f.step"
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
            /><small v-if="f.help">{{ f.help }}</small></label
          >
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="emit('cancel')"
          >
            取消</button
          ><button :disabled="busy">
            {{ busy ? "正在保存…" : spec.submit || "保存" }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
