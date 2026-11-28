import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './assets/main.css'
import './assets/auth.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
const auth = useAuthStore(pinia)
app.use(router)
void auth.initialize()

app.mount('#app')