/**
 * LEMON DROP AI - FRONTEND CLIENT & SPEECH CONTROLLER
 * Decoupled Text-to-Speech + Audio Recorder + Interactive Order Engine
 */

(function () {
    'use strict';

    // ==========================================
    // 1. TextToSpeechProvider (Decoupled TTS)
    // ==========================================
    class BrowserSpeechTTSProvider {
        constructor() {
            this.synth = window.speechSynthesis || null;
            this.enabled = true;
            this.selectedVoice = null;
            this.initVoice();
        }

        initVoice() {
            if (!this.synth) return;
            const updateVoices = () => {
                const voices = this.synth.getVoices();
                // Prefer Colombian or Spanish voice
                this.selectedVoice = voices.find(v => v.lang.includes('es-CO')) ||
                                     voices.find(v => v.lang.includes('es-419')) ||
                                     voices.find(v => v.lang.startsWith('es')) ||
                                     null;
            };
            updateVoices();
            if (speechSynthesis.onvoiceschanged !== undefined) {
                speechSynthesis.onvoiceschanged = updateVoices;
            }
        }

        speak(text) {
            if (!this.enabled || !this.synth || !text) return;
            try {
                this.synth.cancel(); // Cancel any previous speech
                // Strip emojis and URLs for cleaner pronunciation
                const cleanText = text.replace(/([\u2700-\u27BF]|[\uE000-\uF8FF]|\uD83C[\uDC00-\uDFFF]|\uD83D[\uDC00-\uDFFF]|[\u2011-\u26FF]|\uD83E[\uDD10-\uDDFF])/g, '')
                                      .replace(/https?:\/\/\S+/g, '')
                                      .trim();
                if (!cleanText) return;

                const utterance = new SpeechSynthesisUtterance(cleanText);
                if (this.selectedVoice) utterance.voice = this.selectedVoice;
                utterance.lang = 'es-CO';
                utterance.rate = 1.05;
                utterance.pitch = 1.0;
                this.synth.speak(utterance);
            } catch (e) {
                console.warn('TTS not supported or failed:', e);
            }
        }

        toggle() {
            this.enabled = !this.enabled;
            if (!this.enabled && this.synth) this.synth.cancel();
            return this.enabled;
        }
    }

    // ==========================================
    // 2. LemonAI Assistant Controller
    // ==========================================
    class LemonAIAssistant {
        constructor() {
            this.tts = new BrowserSpeechTTSProvider();
            this.conversationId = localStorage.getItem('lemon_ai_conv_id') || null;
            this.clientToken = localStorage.getItem('lemon_ai_client_token') || null;
            this.customerName = localStorage.getItem('lemon_customer_name') || '';
            this.customerPhone = localStorage.getItem('lemon_customer_phone') || '';

            this.mediaRecorder = null;
            this.audioChunks = [];
            this.isRecording = false;

            this.initDOM();
            this.bindEvents();
        }

        initDOM() {
            // Check if widget already exists
            if (document.getElementById('lemon-ai-widget')) return;

            const container = document.createElement('div');
            container.id = 'lemon-ai-widget';
            container.innerHTML = `
                <!-- Launcher Button -->
                <div class="lemon-ai-launcher" id="lemon-ai-launcher" title="Abrir asistente Lemon AI">
                    <span class="launcher-icon">🍋</span>
                    <span>Lemon AI</span>
                    <span class="pulse-indicator"></span>
                </div>

                <!-- Chat Modal Window -->
                <div class="lemon-ai-modal" id="lemon-ai-modal">
                    <!-- Header -->
                    <div class="lemon-ai-header">
                        <div class="lemon-ai-header-left">
                            <div class="lemon-ai-avatar">🍋</div>
                            <div>
                                <div class="lemon-ai-header-title">Lemon Drop AI ✨</div>
                                <div class="lemon-ai-header-subtitle">
                                    <span style="color: #10B981;">●</span> Asesor en línea
                                </div>
                            </div>
                        </div>
                        <div class="lemon-ai-header-actions">
                            <button class="lemon-ai-btn-icon" id="lemon-ai-tts-toggle" title="Activar/Desactivar Voz">🔊</button>
                            <button class="lemon-ai-btn-icon" id="lemon-ai-close" title="Cerrar">✕</button>
                        </div>
                    </div>

                    <!-- Suggestion Chips -->
                    <div class="lemon-ai-chips">
                        <button class="lemon-ai-chip" data-prompt="🍓 Recomiéndame algo dulce">🍓 Algo dulce</button>
                        <button class="lemon-ai-chip" data-prompt="🥭 ¿Qué granizados tienen disponibles?">🥭 Menú disponible</button>
                        <button class="lemon-ai-chip" data-prompt="🔥 ¿Cuál es el más vendido?">🔥 Lo más vendido</button>
                        <button class="lemon-ai-chip" data-prompt="🛒 ¿Qué tengo en mi carrito?">🛒 Ver mi pedido</button>
                        <button class="lemon-ai-chip" data-prompt="🕒 ¿Cuáles son sus horarios?">🕒 Horarios</button>
                    </div>

                    <!-- Messages List -->
                    <div class="lemon-ai-messages" id="lemon-ai-messages">
                        <!-- Welcome message -->
                        <div class="lemon-ai-msg assistant">
                            <div class="lemon-ai-msg-bubble">
                                ¡Hola! 👋🍋 Soy tu asesor de **Lemon Drop**.
                                <br><br>
                                Pídeme lo que quieras por texto o pulsa el 🎙️ micrófono. Por ejemplo:
                                <br>
                                <i>"Quiero un granizado de mango grande con gomitas"</i>
                            </div>
                            <span class="lemon-ai-msg-time">Ahora</span>
                        </div>
                    </div>

                    <!-- Status indicator for voice / thinking -->
                    <div class="lemon-ai-status-indicator" id="lemon-ai-status" style="display: none;">
                        <div class="lemon-ai-soundwave" id="lemon-ai-wave" style="display: none;">
                            <span></span><span></span><span></span><span></span>
                        </div>
                        <span id="lemon-ai-status-text">🧠 Pensando...</span>
                    </div>

                    <!-- Input Footer -->
                    <div class="lemon-ai-footer">
                        <button class="lemon-ai-btn-mic" id="lemon-ai-mic" title="Hablar por micrófono">🎙️</button>
                        <input type="text" class="lemon-ai-input" id="lemon-ai-input" placeholder="Escribe tu pedido o pregunta..." maxlength="500">
                        <button class="lemon-ai-btn-send" id="lemon-ai-send" title="Enviar">➤</button>
                    </div>
                </div>
            `;
            document.body.appendChild(container);
        }

        bindEvents() {
            const launcher = document.getElementById('lemon-ai-launcher');
            const modal = document.getElementById('lemon-ai-modal');
            const closeBtn = document.getElementById('lemon-ai-close');
            const sendBtn = document.getElementById('lemon-ai-send');
            const input = document.getElementById('lemon-ai-input');
            const micBtn = document.getElementById('lemon-ai-mic');
            const ttsBtn = document.getElementById('lemon-ai-tts-toggle');
            const chips = document.querySelectorAll('.lemon-ai-chip');

            launcher.addEventListener('click', () => {
                modal.classList.add('open');
                input.focus();
            });

            closeBtn.addEventListener('click', () => {
                modal.classList.remove('open');
                if (this.tts.synth) this.tts.synth.cancel();
            });

            ttsBtn.addEventListener('click', () => {
                const isEnabled = this.tts.toggle();
                ttsBtn.textContent = isEnabled ? '🔊' : '🔇';
                ttsBtn.title = isEnabled ? 'Voz activada' : 'Voz silenciada';
            });

            sendBtn.addEventListener('click', () => this.handleSendMessage());

            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    this.handleSendMessage();
                }
            });

            micBtn.addEventListener('click', () => this.handleToggleVoice());

            chips.forEach(chip => {
                chip.addEventListener('click', () => {
                    const prompt = chip.getAttribute('data-prompt');
                    if (prompt) {
                        input.value = prompt;
                        this.handleSendMessage();
                    }
                });
            });
        }

        async handleSendMessage(action = null) {
            const input = document.getElementById('lemon-ai-input');
            const text = action ? '' : input.value.trim();

            if (!text && !action) return;

            if (!action) {
                this.appendMessage('user', text);
                input.value = '';
            }

            this.showStatus(true, false, '🧠 Preparando...');

            try {
                const payload = {
                    conversationId: this.conversationId,
                    clientToken: this.clientToken,
                    message: text,
                    customerName: this.customerName,
                    customerPhone: this.customerPhone,
                    action: action
                };

                const response = await fetch('/api/ai/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                const data = await response.json();
                this.showStatus(false);

                if (data.success) {
                    this.updateSession(data.conversationId, data.clientToken);
                    this.handleAIResponse(data);
                } else {
                    this.appendMessage('assistant', data.error || 'Lo siento, tuve un problema temporal. ¿Podrías intentar nuevamente? 🍋');
                }
            } catch (err) {
                console.error('Error in chat request:', err);
                this.showStatus(false);
                this.appendMessage('assistant', 'No logré conectarme con el servidor 😅. Por favor verifica tu conexión a internet.');
            }
        }

        handleAIResponse(data) {
            // Render text message
            if (data.message) {
                this.appendMessage('assistant', data.message);
                this.tts.speak(data.message);
            }

            // Render interactive order review card if requiresConfirmation or order ready
            if ((data.requiresConfirmation || data.orderReadyForConfirmation) && data.cart && data.cart.items && data.cart.items.length > 0) {
                this.renderOrderCard(data.cart);
            }

            // Render success order code and WhatsApp link if order confirmed
            if (data.orderConfirmed && data.orderCode) {
                this.renderSuccessBanner(data.orderCode, data.whatsAppUrl);
            }

            // Dispatch global event for other components on page
            if (data.cartUpdated) {
                window.dispatchEvent(new CustomEvent('lemon:cartUpdated', { detail: data.cart }));
            }
        }

        renderOrderCard(cart) {
            const container = document.getElementById('lemon-ai-messages');
            const card = document.createElement('div');
            card.className = 'lemon-ai-msg assistant';

            let itemsHtml = '';
            cart.items.forEach(item => {
                const toppingsStr = item.addonNames && item.addonNames.length > 0 ? ` + ${item.addonNames.join(', ')}` : '';
                itemsHtml += `
                    <div class="lemon-ai-order-item">
                        <div>
                            <strong>${item.quantity}x ${item.productName} (${item.flavorName || ''})</strong>
                            <div class="lemon-ai-order-item-desc">Tamaño: ${item.size}${toppingsStr}</div>
                        </div>
                        <div><strong>$${Number(item.subtotal).toLocaleString('es-CO')}</strong></div>
                    </div>
                `;
            });

            card.innerHTML = `
                <div class="lemon-ai-order-card">
                    <div class="lemon-ai-order-card-header">
                        <span>🛒 Resumen de tu Pedido</span>
                        <span style="color: #4E9F3D;">${cart.items.length} producto(s)</span>
                    </div>
                    <div class="lemon-ai-order-items-list">
                        ${itemsHtml}
                    </div>
                    <div class="lemon-ai-order-total">
                        <span>Total a pagar:</span>
                        <span>$${Number(cart.total).toLocaleString('es-CO')}</span>
                    </div>
                    <button class="lemon-ai-btn-confirm" id="btn-confirm-order-${Date.now()}">
                        ✅ CONFIRMAR PEDIDO
                    </button>
                    <button class="lemon-ai-btn-modify" id="btn-modify-order-${Date.now()}">
                        ✏️ Seguir modificando
                    </button>
                </div>
            `;

            container.appendChild(card);
            this.scrollToBottom();

            // Bind confirm click
            const confirmBtn = card.querySelector('.lemon-ai-btn-confirm');
            confirmBtn.addEventListener('click', () => {
                confirmBtn.disabled = true;
                confirmBtn.textContent = '⏳ Confirmando pedido...';
                this.handleSendMessage('CONFIRM_ORDER');
            });

            const modifyBtn = card.querySelector('.lemon-ai-btn-modify');
            modifyBtn.addEventListener('click', () => {
                document.getElementById('lemon-ai-input').focus();
            });
        }

        renderSuccessBanner(orderCode, whatsAppUrl) {
            const container = document.getElementById('lemon-ai-messages');
            const banner = document.createElement('div');
            banner.className = 'lemon-ai-msg assistant';

            const waButton = whatsAppUrl ? `
                <div>
                    <a href="${whatsAppUrl}" target="_blank" class="lemon-ai-btn-whatsapp">
                        📱 Ver en WhatsApp
                    </a>
                </div>
            ` : '';

            banner.innerHTML = `
                <div class="lemon-ai-success-banner">
                    <div style="font-size: 1.3rem;">🎉 ¡PEDIDO CONFIRMADO!</div>
                    <div style="margin: 6px 0; font-size: 1.1rem; color: #1E293B;">
                        Código: <strong>${orderCode}</strong>
                    </div>
                    <div style="font-size: 0.85rem; font-weight: normal; color: #334155;">
                        Ya estamos preparando tu pedido con toda la frescura de Lemon Drop 🍋💛
                    </div>
                    ${waButton}
                </div>
            `;

            container.appendChild(banner);
            this.scrollToBottom();
        }

        appendMessage(sender, text) {
            const container = document.getElementById('lemon-ai-messages');
            const msgEl = document.createElement('div');
            msgEl.className = `lemon-ai-msg ${sender}`;

            // Convert simple markdown bold to html bold
            const formattedText = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                      .replace(/\n/g, '<br>');

            const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

            msgEl.innerHTML = `
                <div class="lemon-ai-msg-bubble">${formattedText}</div>
                <span class="lemon-ai-msg-time">${timeStr}</span>
            `;

            container.appendChild(msgEl);
            this.scrollToBottom();
        }

        showStatus(show, isRecording = false, text = '🧠 Pensando...') {
            const statusEl = document.getElementById('lemon-ai-status');
            const waveEl = document.getElementById('lemon-ai-wave');
            const textEl = document.getElementById('lemon-ai-status-text');

            if (show) {
                statusEl.style.display = 'flex';
                waveEl.style.display = isRecording ? 'inline-flex' : 'none';
                textEl.textContent = text;
            } else {
                statusEl.style.display = 'none';
            }
            this.scrollToBottom();
        }

        scrollToBottom() {
            const container = document.getElementById('lemon-ai-messages');
            if (container) {
                container.scrollTop = container.scrollHeight;
            }
        }

        updateSession(convId, token) {
            if (convId) {
                this.conversationId = convId;
                localStorage.setItem('lemon_ai_conv_id', convId);
            }
            if (token) {
                this.clientToken = token;
                localStorage.setItem('lemon_ai_client_token', token);
            }
        }

        // ==========================================
        // 3. Audio Recording & Whisper STT
        // ==========================================
        async handleToggleVoice() {
            const micBtn = document.getElementById('lemon-ai-mic');

            if (this.isRecording) {
                this.stopRecording();
            } else {
                await this.startRecording();
            }
        }

        async startRecording() {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                this.audioChunks = [];
                this.mediaRecorder = new MediaRecorder(stream);

                this.mediaRecorder.ondataavailable = (event) => {
                    if (event.data.size > 0) {
                        this.audioChunks.push(event.data);
                    }
                };

                this.mediaRecorder.onstop = () => {
                    stream.getTracks().forEach(track => track.stop());
                    const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
                    this.sendVoiceAudio(audioBlob);
                };

                this.mediaRecorder.start();
                this.isRecording = true;

                const micBtn = document.getElementById('lemon-ai-mic');
                micBtn.classList.add('recording');
                micBtn.title = 'Pulsar para terminar grabación';
                this.showStatus(true, true, '🔴 Escuchando...');

            } catch (err) {
                console.error('Microphone access denied or error:', err);
                alert('No se pudo acceder al micrófono. Por favor concede permisos de audio en tu navegador.');
            }
        }

        stopRecording() {
            if (this.mediaRecorder && this.isRecording) {
                this.mediaRecorder.stop();
                this.isRecording = false;
                const micBtn = document.getElementById('lemon-ai-mic');
                micBtn.classList.remove('recording');
                micBtn.title = 'Hablar por micrófono';
                this.showStatus(true, false, '🧠 Transcribiendo con Whisper...');
            }
        }

        async sendVoiceAudio(audioBlob) {
            this.showStatus(true, false, '🧠 Procesando tu voz...');

            try {
                const formData = new FormData();
                formData.append('audio', audioBlob, 'voice_command.webm');
                if (this.conversationId) formData.append('conversationId', this.conversationId);
                if (this.clientToken) formData.append('clientToken', this.clientToken);
                if (this.customerName) formData.append('customerName', this.customerName);
                if (this.customerPhone) formData.append('customerPhone', this.customerPhone);

                const response = await fetch('/api/ai/voice', {
                    method: 'POST',
                    body: formData
                });

                const data = await response.json();
                this.showStatus(false);

                if (data.success) {
                    if (data.transcription) {
                        this.appendMessage('user', `🎙️ "${data.transcription}"`);
                    }
                    if (data.chatResponse) {
                        this.updateSession(data.chatResponse.conversationId, data.chatResponse.clientToken);
                        this.handleAIResponse(data.chatResponse);
                    }
                } else {
                    this.appendMessage('assistant', data.error || 'No pude procesar el audio correctamente 🎙️.');
                }

            } catch (err) {
                console.error('Error sending voice audio:', err);
                this.showStatus(false);
                this.appendMessage('assistant', 'Ocurrió un error al enviar el audio.');
            }
        }
    }

    // Initialize Lemon AI on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => new LemonAIAssistant());
    } else {
        new LemonAIAssistant();
    }
})();
