FROM node:24.11.0-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
