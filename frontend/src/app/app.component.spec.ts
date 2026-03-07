import { render } from '@testing-library/angular';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  it('should create the app', async () => {
    const { fixture } = await render(AppComponent, {
      providers: [provideHttpClient(), provideRouter([])],
    });
    expect(fixture.componentInstance).toBeTruthy();
  });
});
