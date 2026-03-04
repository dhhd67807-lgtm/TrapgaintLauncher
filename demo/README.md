# Zalith Launcher - White Blossom Edition Demo

A modern, clean HTML/CSS/JS frontend prototype of the Zalith Launcher with a minimalist white aesthetic.

## 🌸 Design Philosophy

The White Blossom theme embraces:
- **Minimalism**: Clean, uncluttered interface with focus on content
- **Whitespace**: Generous spacing for better readability
- **Contrast**: Black text on white backgrounds for maximum clarity
- **Simplicity**: Intuitive navigation and clear visual hierarchy
- **Elegance**: Subtle shadows and smooth transitions

## 🎨 Color Palette

```css
Primary Background: #FFFFFF (Pure White)
Secondary Background: #FAFAFA (Off White)
Tertiary Background: #F5F5F5 (Light Gray)
Borders: #E0E0E0 (Medium Gray)
Text Primary: #000000 (Black)
Text Secondary: #666666 (Dark Gray)
Text Tertiary: #999999 (Medium Gray)
Accent: #000000 (Black)
```

## 📁 File Structure

```
demo/
├── index.html      # Main HTML structure
├── styles.css      # White Blossom theme styles
├── script.js       # Interactive functionality
└── README.md       # This file
```

## 🚀 Features

### Implemented Pages
- ✅ Home Dashboard (with quick actions, version info, activity feed)
- ✅ Versions Manager (list and search versions)
- ✅ Mods (placeholder)
- ✅ Settings (placeholder)
- ✅ Account (placeholder)
- ✅ Files (placeholder)
- ✅ About (with branding info)

### Interactive Elements
- Smooth page transitions
- Hover effects on cards and buttons
- Click ripple effects
- Search functionality
- Keyboard navigation (Arrow Up/Down)
- Notification system
- Responsive design

## 🎯 How to Use

1. **Open the demo**: Simply open `index.html` in any modern web browser
2. **Navigate**: Click menu items on the left sidebar to switch pages
3. **Interact**: Try clicking buttons, searching versions, and hovering over elements
4. **Customize**: Edit `styles.css` to rebrand colors, fonts, and spacing

## 🎨 Rebranding Guide

### Change Colors
Edit the CSS variables in `styles.css`:

```css
:root {
    --white: #FFFFFF;           /* Main background */
    --black: #000000;           /* Accent color */
    --text-primary: #000000;    /* Main text */
    /* ... modify as needed */
}
```

### Change Fonts
Replace the Google Fonts import in `index.html`:

```html
<link href="https://fonts.googleapis.com/css2?family=YourFont:wght@300;400;500;600;700&display=swap" rel="stylesheet">
```

Then update the font-family in `styles.css`:

```css
body {
    font-family: 'YourFont', sans-serif;
}
```

### Change Logo
Replace the SVG in the `.logo` section of `index.html` with your own logo.

### Modify Layout
- Sidebar width: Change `.sidebar { width: 280px; }`
- Card spacing: Modify `.content-grid { gap: 24px; }`
- Border radius: Update `--border-radius: 12px;`

## 📱 Responsive Design

The demo is responsive and works on:
- Desktop (1920px+)
- Laptop (1366px+)
- Tablet (768px+)
- Mobile (320px+)

## 🔧 Customization Examples

### Example 1: Change to Cream Theme
```css
:root {
    --white: #FFF8F0;
    --off-white: #FFF5E8;
    --light-gray: #F5EFE7;
}
```

### Example 2: Add Brand Color
```css
:root {
    --brand-color: #FF6B6B;
}

.action-btn.primary {
    background: var(--brand-color);
}
```

### Example 3: Rounded Design
```css
:root {
    --border-radius: 20px;
}

.card {
    border-radius: var(--border-radius);
}
```

## 🎬 Animation Details

- Page transitions: 300ms fade-in
- Hover effects: 300ms cubic-bezier easing
- Button ripples: 600ms scale animation
- Notifications: Slide in from bottom

## 📝 Notes

- This is a **frontend-only** demo with no backend functionality
- All data is static/placeholder content
- Perfect for design review and rebranding discussions
- Can be used as a reference for Android implementation

## 🔄 Converting to Android

To implement this design in the Android app:

1. **Colors**: Copy color values to `colors.xml`
2. **Layout**: Use ConstraintLayout/LinearLayout to match structure
3. **Typography**: Set font sizes and weights in `styles.xml`
4. **Spacing**: Use dp values matching the design
5. **Animations**: Implement with Android Animation API

## 📄 License

This demo follows the same license as Zalith Launcher (GPL v3).

## 🤝 Contributing

Feel free to:
- Modify colors and styles
- Add new pages
- Improve animations
- Enhance responsiveness
- Add more interactive features

---

**White Blossom Edition v1.4.1.2**  
*Clean. Simple. Elegant.*
