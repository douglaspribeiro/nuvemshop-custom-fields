# Borrador para soporte técnico de Tiendanube — Patagonia

Estado: preparado, no enviado.

El tema Patagonia permite agregar productos sin personalización aunque la app haya habilitado `handle_cart_before_update: true`. Necesitamos que las operaciones nativas pasen por `cart:before_update` antes de modificar el carrito.

## Reproducción

- Tienda de prueba: `cajaspersonalizadas2.mitiendanube.com`, ID 8398782.
- Producto: 379192178; variante única: 1633744953.
- Campo SELECT obligatorio: “Moido ou Granel”, opciones En Grano y Molido.
- Runtime observado: NubeSDK 0.56.0.
- La app envía `config:set` con `handle_cart_before_update: true` al iniciar, y registra el listener sin esperar consultas remotas.
- El botón nativo de compra rápida agrega directamente. Se observan `cart:update` y `cart:add:success`, pero no `cart:before_update` ni `quickbuy:open`.
- En el PDP, `getCustomization().set("add-to-cart-button", {disabled:true})` devolvió `applied:1`; `get` devolvió `disabled:true`, y se observó `aria-disabled=true`. Aun así, un clic del navegador agregó una línea sin propiedades.
- La acción propia de la app con `cart:add` y `properties` sí conserva las selecciones hasta el checkout.

## Resultado esperado

Que el botón nativo del PDP y el de la grilla respeten el gate de `cart:before_update` y que `disabled:true` impida realmente la compra, según la documentación oficial. Solicitamos una solución aplicable desde la app, sin cambios manuales de CSS por tienda.

Mientras se investiga, nuestro checkout no bloquea compras por propiedades ausentes, especialmente órdenes precargadas con `from_store=1`. No se finalizó ningún pedido ni pago en estas pruebas.

Referencias: [eventos del carrito](https://tiendanube.dev/apps/nube-sdk/events/cart) y [API de personalización](https://tiendanube.dev/es-AR/apps/nube-sdk/api/customization).
