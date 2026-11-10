FROM node:24-bookworm-slim
WORKDIR /srv/shadow
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force
COPY shadow.mjs ./
RUN mkdir -p /srv/shadow/private && chown -R node:node /srv/shadow
ENV JH2_MEGA_SHADOW_PORT=48721
EXPOSE 48721
USER node
CMD ["node", "shadow.mjs"]