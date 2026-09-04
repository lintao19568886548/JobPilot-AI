import { createApp } from './app.js'
import { loadConfig } from './config.js'

const config = loadConfig()
createApp(config).listen(config.port, '127.0.0.1', () => {
  process.stdout.write(`AUTOMATION_WORKER=http://127.0.0.1:${config.port} enabled=${config.enabled}\n`)
})
