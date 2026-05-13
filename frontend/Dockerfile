## Build stage
FROM node:20-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm install --ignore-scripts
COPY . .
RUN npm run build

## Runtime stage
FROM nginx:1.27-alpine
COPY --from=build /src/dist/freepeepee /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
