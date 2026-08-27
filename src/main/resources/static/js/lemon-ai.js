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
                this.synth.cancel();
                // Strip markdown tables, emojis and URLs for cleaner pronunciation
                let cleanText = text.replace(/\|.*?\|/g, '')
                                    .replace(/([\u2700-\u27BF]|[\uE000-\uF8FF]|\uD83C[\uDC00-\uDFFF]|\uD83D[\uDC00-\uDFFF]|[\u2011-\u26FF]|\uD83E[\uDD10-\uDDFF])/g, '')
                                    .replace(/https?:\/\/\S+/g, '')
                                    .replace(/[*_#`~]/g, '')
                                    .trim();
                if (!cleanText) return;

                const utterance = new SpeechSynthesisUtterance(cleanText);
                if (this.selectedVoice) utterance.voice = this.selectedVoice;
                utterance.lang = 'es-CO';
                utterance.rate = 1.05;
                utterance.pitch = 1.0;
                this.synth.speak(utterance);
            } catch (e) {
                console.warn('TTS warning:', e);
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
            if (document.getElementById('lemon-ai-widget')) return;

            const container = document.createElement('div');
            container.id = 'lemon-ai-widget';
            container.innerHTML = `
                <!-- Floating Mascot Launcher -->
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
                                    <span style="color: #10B981; font-size: 0.9rem;">●</span> Asesor inteligente en línea
                                </div>
                            </div>
                        </div>
                        <div class="lemon-ai-header-actions">
                            <button class="lemon-ai-btn-icon" id="lemon-ai-tts-toggle" title="Activar/Desactivar Voz">🔊</button>
                            <button class="lemon-ai-btn-icon" id="lemon-ai-close" title="Cerrar ventana">✕</button>
                        </div>
                    </div>

                    <!-- Suggestion Chips Bar -->
                    <div class="lemon-ai-chips-bar">
                        <button class="lemon-ai-chip" data-prompt="🍓 Recomiéndame algo dulce">🍓 Algo dulce</button>
                        <button class="lemon-ai-chip" data-prompt="🥭 ¿Qué sabores tienen disponibles?">🥭 Menú disponible</button>
                        <button class="lemon-ai-chip" data-prompt="🔥 ¿Cuál es el más vendido?">🔥 Lo más vendido</button>
                        <button class="lemon-ai-chip" data-prompt="🛒 ¿Qué tengo en mi carrito?">🛒 Mi pedido</button>
                        <button class="lemon-ai-chip" data-prompt="🕒 ¿Cuáles son sus horarios?">🕒 Horarios</button>
                    </div>

                    <!-- Messages List -->
                    <div class="lemon-ai-messages" id="lemon-ai-messages">
                        <!-- Welcome message -->
                        <div class="lemon-ai-msg assistant">
                            <div class="lemon-ai-msg-bubble">
                                ¡Hola! 🍋 ¿Qué granizado se te antoja hoy? Puedes pedirme un sabor, armar tu vaso o pedir una recomendación. 😄
                            </div>
                        </div>
                    </div>

                    <!-- Voice Waveform Overlay -->
                    <div class="lemon-ai-voice-overlay" id="lemon-ai-voice-overlay">
                        <div style="font-size: 2rem;">🍋</div>
                        <div style="font-weight: 700; font-size: 1.1rem;" id="lemon-ai-voice-status">Escuchando tu voz...</div>
                        <div class="lemon-ai-waveform">
                            <div class="lemon-ai-wave-bar"></div>
                            <div class="lemon-ai-wave-bar"></div>
                            <div class="lemon-ai-wave-bar"></div>
                            <div class="lemon-ai-wave-bar"></div>
                            <div class="lemon-ai-wave-bar"></div>
                            <div class="lemon-ai-wave-bar"></div>
                        </div>
                        <button class="lemon-ai-chip" id="lemon-ai-voice-stop-btn" style="background: #FFD91A; color: #0F2818; margin-top: 10px;">
                            ⏹️ Terminar y Procesar
                        </button>
                    </div>

                    <!-- Input Footer -->
                    <div class="lemon-ai-input-area">
                        <button class="lemon-ai-mic-btn" id="lemon-ai-mic" title="Hablar por voz">🎙️</button>
                        <input type="text" class="lemon-ai-text-input" id="lemon-ai-input" placeholder="Escribe tu pedido o pregunta..." maxlength="500">
                        <button class="lemon-ai-send-btn" id="lemon-ai-send" title="Enviar mensaje">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                <line x1="22" y1="2" x2="11" y2="13"></line>
                                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                            </svg>
                        </button>
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
            const voiceStopBtn = document.getElementById('lemon-ai-voice-stop-btn');
            const chips = document.querySelectorAll('.lemon-ai-chip[data-prompt]');

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
            if (voiceStopBtn) {
                voiceStopBtn.addEventListener('click', () => this.stopRecording());
            }

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

            this.showTypingIndicator(true);

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
                this.showTypingIndicator(false);

                if (data.success) {
                    this.updateSession(data.conversationId, data.clientToken);
                    this.handleAIResponse(data);
                } else {
                    this.appendMessage('assistant', data.error || 'Lo siento, tuve un problema temporal. ¿Podrías intentar nuevamente? 🍋');
                }
            } catch (err) {
                console.error('Error in chat request:', err);
                this.showTypingIndicator(false);
                this.appendMessage('assistant', 'No logré conectarme con el servidor 😅. Por favor verifica tu conexión.');
            }
        }

        handleAIResponse(data) {
            // Render text message with Markdown formatting & table-to-card conversion
            if (data.message) {
                this.appendMessage('assistant', data.message);
                this.tts.speak(data.message);
            }

            // Render interactive product cards if provided by backend
            if (data.products && Array.isArray(data.products) && data.products.length > 0) {
                this.renderProductCards(data.products);
            }

            // If structured cart data is present and confirmation is required, render order card
            if ((data.requiresConfirmation || data.orderReadyForConfirmation) && data.cart && data.cart.items && data.cart.items.length > 0) {
                this.renderStructuredOrderCard(data.cart);
            }

            // If order was confirmed, render success banner with WhatsApp button
            if (data.orderConfirmed && data.orderCode) {
                this.renderSuccessBanner(data.orderCode, data.whatsAppUrl);
            }

            if (data.cartUpdated) {
                window.dispatchEvent(new CustomEvent('lemon:cartUpdated', { detail: data.cart }));
            }
        }

        renderProductCards(products) {
            const container = document.getElementById('lemon-ai-messages');
            const wrapper = document.createElement('div');
            wrapper.className = 'lemon-ai-msg assistant';

            let cardsHtml = '';
            products.forEach(p => {
                const badgeHtml = p.badge ? `<span class="lemon-ai-prod-badge">${p.badge}</span>` : '';
                const priceFormatted = Number(p.priceFrom || 0).toLocaleString('es-CO');
                const imgHtml = p.image ? `<img src="${p.image}" alt="${p.name}" class="lemon-ai-prod-img" onerror="this.style.display='none'">` : '<div class="lemon-ai-prod-icon">🍋</div>';

                cardsHtml += `
                    <div class="lemon-ai-prod-card" data-product-name="${p.name}">
                        ${badgeHtml}
                        <div class="lemon-ai-prod-thumb">
                            ${imgHtml}
                        </div>
                        <div class="lemon-ai-prod-info">
                            <div class="lemon-ai-prod-name">${p.name}</div>
                            <div class="lemon-ai-prod-desc">${p.description || ''}</div>
                            <div class="lemon-ai-prod-footer">
                                <span class="lemon-ai-prod-price">Desde $${priceFormatted}</span>
                                <button class="lemon-ai-prod-btn" type="button">Pedir 🍧</button>
                            </div>
                        </div>
                    </div>
                `;
            });

            wrapper.innerHTML = `
                <div class="lemon-ai-products-carousel">
                    ${cardsHtml}
                </div>
            `;

            container.appendChild(wrapper);
            this.scrollToBottom();

            // Bind click events on product cards
            wrapper.querySelectorAll('.lemon-ai-prod-card').forEach(card => {
                card.addEventListener('click', () => {
                    const prodName = card.getAttribute('data-product-name');
                    if (prodName) {
                        const input = document.getElementById('lemon-ai-input');
                        input.value = `Quiero un ${prodName}`;
                        this.handleSendMessage();
                    }
                });
            });
        }

        renderStructuredOrderCard(cart) {
            const container = document.getElementById('lemon-ai-messages');
            const cardWrapper = document.createElement('div');
            cardWrapper.className = 'lemon-ai-msg assistant';

            let itemsHtml = '';
            cart.items.forEach(item => {
                const toppingsStr = item.addonNames && item.addonNames.length > 0 
                    ? `<div style="font-size: 0.8rem; color: #2F7D32; font-weight: 600;">+ ${item.addonNames.join(', ')}</div>` 
                    : '';

                itemsHtml += `
                    <div class="lemon-ai-order-item-row">
                        <div>
                            <strong>${item.quantity}x ${item.productName}</strong>
                            <div style="font-size: 0.82rem; color: #64748B;">Sabor: ${item.flavorName || 'Tradicional'} • Tamaño: ${item.size}</div>
                            ${toppingsStr}
                        </div>
                        <div style="font-weight: 700; color: #173B24;">$${Number(item.subtotal).toLocaleString('es-CO')}</div>
                    </div>
                `;
            });

            cardWrapper.innerHTML = `
                <div class="lemon-ai-order-card">
                    <div class="lemon-ai-order-card-header">
                        <span>🛒 Resumen de tu Pedido</span>
                        <span style="color: #2F7D32; font-size: 0.85rem;">${cart.items.length} producto(s)</span>
                    </div>
                    <div>
                        ${itemsHtml}
                    </div>
                    <div class="lemon-ai-order-card-total">
                        <span>Total:</span>
                        <span>$${Number(cart.total).toLocaleString('es-CO')}</span>
                    </div>
                    <div class="lemon-ai-order-card-actions">
                        <button class="lemon-ai-order-card-btn lemon-ai-btn-confirm" id="btn-confirm-ai-${Date.now()}">
                            ✅ Confirmar Pedido
                        </button>
                        <button class="lemon-ai-order-card-btn lemon-ai-btn-cancel" id="btn-modify-ai-${Date.now()}">
                            ✏️ Modificar
                        </button>
                    </div>
                </div>
            `;

            container.appendChild(cardWrapper);
            this.scrollToBottom();

            const confirmBtn = cardWrapper.querySelector('.lemon-ai-btn-confirm');
            confirmBtn.addEventListener('click', () => {
                confirmBtn.disabled = true;
                confirmBtn.textContent = '⏳ Confirmando pedido...';
                this.handleSendMessage('CONFIRM_ORDER');
            });

            const modifyBtn = cardWrapper.querySelector('.lemon-ai-btn-cancel');
            modifyBtn.addEventListener('click', () => {
                document.getElementById('lemon-ai-input').focus();
            });
        }

        renderSuccessBanner(orderCode, whatsAppUrl) {
            const container = document.getElementById('lemon-ai-messages');
            const banner = document.createElement('div');
            banner.className = 'lemon-ai-msg assistant';

            const waButton = whatsAppUrl ? `
                <a href="${whatsAppUrl}" target="_blank" class="ld-btn ld-btn-lemon ld-btn-sm" style="flex: 1; text-align: center; font-size: 0.82rem;">
                    📱 Abrir WhatsApp
                </a>
            ` : '';

            banner.innerHTML = `
                <div class="lemon-ai-order-card" style="border-color: #2F7D32; background: #FFFDF7;">
                    <div style="font-size: 1.15rem; font-weight: 800; color: #173B24; text-align: center;">
                        🎉 PEDIDO RECIBIDO
                    </div>
                    <div style="text-align: center; margin: 6px 0;">
                        <span class="ld-badge ld-badge-lemon" style="font-size: 1rem; padding: 5px 14px;">
                            ${orderCode}
                        </span>
                    </div>
                    <div style="background: #E8F5E9; border-radius: 8px; padding: 6px 10px; margin: 6px 0; font-size: 0.82rem; color: #1B5E20; text-align: center; font-weight: 700;">
                        🟢 Estado: Pedido recibido
                    </div>
                    <p style="font-size: 0.82rem; color: #64748B; text-align: center; line-height: 1.4; margin-bottom: 8px;">
                        Tu pedido fue registrado correctamente. Te avisaremos por WhatsApp cuando esté listo para recoger.
                    </p>
                    <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                        <a href="/pedido/seguimiento/${orderCode}" target="_blank" class="ld-btn ld-btn-outline ld-btn-sm" style="flex: 1; text-align: center; font-size: 0.82rem;">
                            🔎 Seguimiento
                        </a>
                        ${waButton}
                    </div>
                </div>
            `;

            container.appendChild(banner);
            this.scrollToBottom();
        }

        appendMessage(sender, text) {
            const container = document.getElementById('lemon-ai-messages');
            const msgEl = document.createElement('div');
            msgEl.className = `lemon-ai-msg ${sender}`;

            // Parse markdown tables and formatting
            const formattedContent = this.formatContent(text);

            msgEl.innerHTML = `
                <div class="lemon-ai-msg-bubble">${formattedContent}</div>
            `;

            container.appendChild(msgEl);
            this.scrollToBottom();
        }

        formatContent(text) {
            if (!text) return '';

            // Check if text contains a markdown table
            if (text.includes('|') && text.includes('---')) {
                return this.parseMarkdownTableToHtml(text);
            }

            // Standard Markdown formatting
            return text
                .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                .replace(/\*(.*?)\*/g, '<em>$1</em>')
                .replace(/\n/g, '<br>');
        }

        parseMarkdownTableToHtml(text) {
            const lines = text.split('\n');
            let beforeTable = [];
            let tableLines = [];
            let afterTable = [];
            let inTable = false;

            for (let line of lines) {
                if (line.trim().startsWith('|') && line.trim().endsWith('|')) {
                    inTable = true;
                    tableLines.push(line.trim());
                } else if (inTable) {
                    afterTable.push(line);
                } else {
                    beforeTable.push(line);
                }
            }

            let html = '';
            if (beforeTable.length > 0) {
                html += beforeTable.join('<br>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') + '<br>';
            }

            if (tableLines.length >= 2) {
                const headers = tableLines[0].split('|').map(s => s.trim()).filter(s => s.length > 0);
                const rows = tableLines.slice(2); // skip header and separator

                html += '<div class="lemon-ai-order-card" style="margin: 10px 0;">';
                rows.forEach(rowStr => {
                    const cols = rowStr.split('|').map(s => s.trim()).filter(s => s.length > 0);
                    if (cols.length >= 2) {
                        html += `
                            <div class="lemon-ai-order-item-row">
                                <div>
                                    <strong>${cols[0]}x ${cols[1] || 'Granizado'}</strong>
                                    <div style="font-size: 0.8rem; color: #64748B;">${cols[2] || ''} ${cols[4] ? '• ' + cols[4] : ''}</div>
                                </div>
                                <div style="font-weight: 700; color: #173B24;">${cols[cols.length - 1] || ''}</div>
                            </div>
                        `;
                    }
                });
                html += '</div>';
            }

            if (afterTable.length > 0) {
                html += afterTable.join('<br>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
            }

            return html;
        }

        showTypingIndicator(show) {
            let typingEl = document.getElementById('lemon-ai-typing-indicator');
            if (show) {
                if (!typingEl) {
                    typingEl = document.createElement('div');
                    typingEl.id = 'lemon-ai-typing-indicator';
                    typingEl.className = 'lemon-ai-typing';
                    typingEl.innerHTML = `
                        <span class="lemon-ai-typing-dot"></span>
                        <span class="lemon-ai-typing-dot"></span>
                        <span class="lemon-ai-typing-dot"></span>
                    `;
                    document.getElementById('lemon-ai-messages').appendChild(typingEl);
                }
            } else {
                if (typingEl) typingEl.remove();
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

                const overlay = document.getElementById('lemon-ai-voice-overlay');
                if (overlay) overlay.classList.add('active');

            } catch (err) {
                console.error('Microphone access denied or error:', err);
                alert('No se pudo acceder al micrófono. Por favor concede permisos de audio en tu navegador.');
            }
        }

        stopRecording() {
            if (this.mediaRecorder && this.isRecording) {
                this.mediaRecorder.stop();
                this.isRecording = false;

                const statusText = document.getElementById('lemon-ai-voice-status');
                if (statusText) statusText.textContent = '🧠 Procesando tu voz...';
            }
        }

        async sendVoiceAudio(audioBlob) {
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
                
                const overlay = document.getElementById('lemon-ai-voice-overlay');
                if (overlay) {
                    overlay.classList.remove('active');
                    const statusText = document.getElementById('lemon-ai-voice-status');
                    if (statusText) statusText.textContent = 'Escuchando tu voz...';
                }

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
                const overlay = document.getElementById('lemon-ai-voice-overlay');
                if (overlay) overlay.classList.remove('active');
                this.appendMessage('assistant', 'Ocurrió un error al enviar el audio.');
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => new LemonAIAssistant());
    } else {
        new LemonAIAssistant();
    }
})();
