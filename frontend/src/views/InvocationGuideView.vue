<script setup lang="ts">
import { computed, ref } from 'vue'
import { CopyDocument, Key, Lock, Timer, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'

const method = ref('POST')
const path = ref('/open-api/example')
const rawQuery = ref('')
const body = ref('{"example":"value"}')
const origin = window.location.origin
const timestampExample = '1723257600000'
const nonceExample = '550e8400-e29b-41d4-a716-446655440000'
const bodyHashExample = 'SHA256_HEX(请求体原始字节)'

const canonical = computed(() => [method.value, path.value, rawQuery.value, timestampExample, nonceExample, bodyHashExample].join('\n'))
const curlExample = computed(() => `curl -X ${method.value} '${origin}${path.value}${rawQuery.value ? `?${rawQuery.value}` : ''}' \\
  -H 'Content-Type: application/json' \\
  -H 'X-App-Key: YOUR_APP_KEY' \\
  -H 'X-Timestamp: ${timestampExample}' \\
  -H 'X-Nonce: ${nonceExample}' \\
  -H 'X-Signature: HMAC_SHA256_HEX' \\
  -H 'X-Trace-Id: optional-business-trace-id' \\
  --data '${body.value}'`)

const javaExample = computed(() => `import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class InterfacePlatformClient {
    private static final String APP_KEY = System.getenv("INTERFACE_APP_KEY");
    private static final String APP_SECRET = System.getenv("INTERFACE_APP_SECRET");

    public static void main(String[] args) throws Exception {
        String method = "${method.value}";
        String path = "${path.value}";
        String rawQuery = "${rawQuery.value}";
        String body = ${JSON.stringify(body.value)};
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body);
        String canonical = String.join("\\n", method, path, rawQuery, timestamp, nonce, bodyHash);
        String signature = hmacSha256Hex(APP_SECRET, canonical);

        URI uri = URI.create("${origin}" + path + (rawQuery.isEmpty() ? "" : "?" + rawQuery));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .header("X-App-Key", APP_KEY)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode() + " " + response.body());
    }

    static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    static String hmacSha256Hex(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}`)

async function copy(value: string, label: string): Promise<void> {
  await navigator.clipboard.writeText(value)
  ElMessage.success(`${label}已复制`)
}
</script>

<template>
  <div class="view-stack guide-view">
    <PageHeader eyebrow="INTEGRATION GUIDE" title="开放接口调用指南" description="外部系统使用 AppKey 标识身份，并用 AppSecret 对每次请求做 HMAC-SHA256 签名。" />

    <section class="guide-steps">
      <article><el-icon><Key /></el-icon><div><b>1. 申请凭证</b><span>管理员创建调用方并安全交付 AppKey、AppSecret</span></div></article>
      <article><el-icon><Lock /></el-icon><div><b>2. 分配权限</b><span>按接口编码授权 HTTP 或 SQL 资源</span></div></article>
      <article><el-icon><Timer /></el-icon><div><b>3. 请求签名</b><span>每次生成时间戳与唯一 Nonce，计算 HMAC 签名</span></div></article>
    </section>

    <el-alert title="安全要求" description="AppSecret 只能保存在调用系统的安全配置中，不能放在浏览器前端、URL、日志或源代码仓库。时间戳与平台时间相差超过 5 分钟，或 Nonce 被重复使用，请求都会被拒绝。" type="warning" :closable="false" show-icon />

    <section class="panel guide-section">
      <div class="panel__header"><div><h2>签名请求头</h2><p>所有 `/open-api/**` 请求都必须携带前五个请求头</p></div></div>
      <div class="header-table">
        <div><code>X-App-Key</code><b>必填</b><span>调用方公开标识</span></div>
        <div><code>X-Timestamp</code><b>必填</b><span>Unix 毫秒时间戳，允许前后 5 分钟</span></div>
        <div><code>X-Nonce</code><b>必填</b><span>每次请求唯一的随机字符串，推荐 UUID</span></div>
        <div><code>X-Signature</code><b>必填</b><span>使用 AppSecret 计算的 HMAC-SHA256 小写十六进制值</span></div>
        <div><code>Content-Type</code><b>必填</b><span>有请求体时使用 application/json</span></div>
        <div><code>X-Trace-Id</code><em>可选</em><span>业务方传入链路标识；不传时由平台生成</span></div>
      </div>
    </section>

    <section class="panel guide-section">
      <div class="panel__header"><div><h2>签名原文 Canonical Request</h2><p>以下六行严格按顺序拼接，行之间只使用 \n，最后一行后不加换行</p></div><el-button :icon="CopyDocument" @click="copy(canonical, '签名原文')">复制</el-button></div>
      <div class="canonical-builder">
        <div class="form-grid"><el-form-item label="HTTP 方法"><el-select v-model="method" style="width:100%"><el-option v-for="item in ['GET','POST','PUT','PATCH','DELETE']" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="开放路径"><el-input v-model="path" /></el-form-item></div>
        <el-form-item label="原始查询串（不含 ?，没有则留空）"><el-input v-model="rawQuery" placeholder="warehouseId=1&sku=A001" /></el-form-item>
        <el-form-item label="示例 JSON 请求体"><el-input v-model="body" type="textarea" :rows="3" /></el-form-item>
        <pre>{{ canonical }}</pre>
        <div class="signature-formula"><span>请求体摘要</span><code>SHA256_HEX(rawBodyBytes)</code><span>最终签名</span><code>HMAC_SHA256_HEX(AppSecret, canonicalRequest)</code></div>
      </div>
    </section>

    <section class="panel guide-section">
      <div class="panel__header"><div><h2>调用示例</h2><p>生产环境请从环境变量或密钥服务读取 AppSecret</p></div></div>
      <el-tabs class="code-tabs">
        <el-tab-pane label="Java 17">
          <div class="code-toolbar"><span>完整签名与调用示例</span><el-button :icon="CopyDocument" link type="primary" @click="copy(javaExample, 'Java 示例')">复制代码</el-button></div>
          <pre>{{ javaExample }}</pre>
        </el-tab-pane>
        <el-tab-pane label="curl">
          <div class="code-toolbar"><span>先按同一规则计算 X-Signature，再执行请求</span><el-button :icon="CopyDocument" link type="primary" @click="copy(curlExample, 'curl 示例')">复制命令</el-button></div>
          <pre>{{ curlExample }}</pre>
        </el-tab-pane>
      </el-tabs>
    </section>

    <section class="guide-errors">
      <el-icon><Warning /></el-icon><div><b>常见拒绝原因</b><span>401：AppKey、时间戳、Nonce 或签名无效；403：调用方未获得该接口权限；404：开放路径不存在或未启用。使用响应头中的 X-Trace-Id 到“调用日志”定位详情。</span></div>
    </section>
  </div>
</template>
