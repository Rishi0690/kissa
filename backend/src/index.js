// Worker entry: only a default export (workerd rejects extra named exports on the entry module).
import { serve } from './app.js';

export default {
  fetch(request, env) { return serve(request, env); },
};
