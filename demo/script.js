// White Blossom Theme - Zalith Launcher Demo
// Navigation functionality

document.addEventListener('DOMContentLoaded', () => {
    // Get all menu items and pages
    const menuItems = document.querySelectorAll('.menu-item');
    const pages = document.querySelectorAll('.page');

    // Handle menu item clicks
    menuItems.forEach(item => {
        item.addEventListener('click', () => {
            const targetPage = item.getAttribute('data-page');

            // Remove active class from all menu items
            menuItems.forEach(mi => mi.classList.remove('active'));

            // Add active class to clicked item
            item.classList.add('active');

            // Hide all pages
            pages.forEach(page => page.classList.remove('active'));

            // Show target page
            const targetPageElement = document.getElementById(`${targetPage}-page`);
            if (targetPageElement) {
                targetPageElement.classList.add('active');
            }

            // Add smooth animation
            targetPageElement.style.animation = 'none';
            setTimeout(() => {
                targetPageElement.style.animation = 'fadeIn 0.3s ease-in-out';
            }, 10);
        });
    });

    // Add hover effects to cards
    const cards = document.querySelectorAll('.card');
    cards.forEach(card => {
        card.addEventListener('mouseenter', () => {
            card.style.transform = 'translateY(-4px)';
        });

        card.addEventListener('mouseleave', () => {
            card.style.transform = 'translateY(0)';
        });
    });

    // Add click effects to action buttons
    const actionButtons = document.querySelectorAll('.action-btn');
    actionButtons.forEach(button => {
        button.addEventListener('click', (e) => {
            // Create ripple effect
            const ripple = document.createElement('span');
            const rect = button.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const x = e.clientX - rect.left - size / 2;
            const y = e.clientY - rect.top - size / 2;

            ripple.style.width = ripple.style.height = size + 'px';
            ripple.style.left = x + 'px';
            ripple.style.top = y + 'px';
            ripple.classList.add('ripple');

            button.appendChild(ripple);

            setTimeout(() => {
                ripple.remove();
            }, 600);

            // Show notification
            showNotification(button.querySelector('span').textContent);
        });
    });

    // Search functionality
    const searchInput = document.querySelector('.search-bar input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const searchTerm = e.target.value.toLowerCase();
            const versionItems = document.querySelectorAll('.version-item');

            versionItems.forEach(item => {
                const text = item.textContent.toLowerCase();
                if (text.includes(searchTerm)) {
                    item.style.display = 'flex';
                } else {
                    item.style.display = 'none';
                }
            });
        });
    }

    // Version item click handler
    const versionItems = document.querySelectorAll('.version-item');
    versionItems.forEach(item => {
        item.addEventListener('click', () => {
            versionItems.forEach(vi => vi.classList.remove('active'));
            item.classList.add('active');
            showNotification('Version selected');
        });
    });

    // Notification system
    function showNotification(message) {
        // Remove existing notification
        const existingNotification = document.querySelector('.notification');
        if (existingNotification) {
            existingNotification.remove();
        }

        // Create notification
        const notification = document.createElement('div');
        notification.className = 'notification';
        notification.textContent = message;
        document.body.appendChild(notification);

        // Style notification
        Object.assign(notification.style, {
            position: 'fixed',
            bottom: '24px',
            right: '24px',
            background: '#000',
            color: '#fff',
            padding: '16px 24px',
            borderRadius: '8px',
            boxShadow: '0 8px 24px rgba(0, 0, 0, 0.2)',
            zIndex: '1000',
            animation: 'slideIn 0.3s ease-out',
            fontSize: '14px',
            fontWeight: '500'
        });

        // Remove after 3 seconds
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-out';
            setTimeout(() => {
                notification.remove();
            }, 300);
        }, 3000);
    }

    // Add CSS animations
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from {
                transform: translateY(100px);
                opacity: 0;
            }
            to {
                transform: translateY(0);
                opacity: 1;
            }
        }

        @keyframes slideOut {
            from {
                transform: translateY(0);
                opacity: 1;
            }
            to {
                transform: translateY(100px);
                opacity: 0;
            }
        }

        .ripple {
            position: absolute;
            border-radius: 50%;
            background: rgba(255, 255, 255, 0.5);
            transform: scale(0);
            animation: rippleEffect 0.6s ease-out;
            pointer-events: none;
        }

        @keyframes rippleEffect {
            to {
                transform: scale(4);
                opacity: 0;
            }
        }

        .action-btn {
            position: relative;
            overflow: hidden;
        }
    `;
    document.head.appendChild(style);

    // Add keyboard navigation
    document.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            const activeMenuItem = document.querySelector('.menu-item.active');
            const menuItemsArray = Array.from(menuItems);
            const currentIndex = menuItemsArray.indexOf(activeMenuItem);

            let newIndex;
            if (e.key === 'ArrowUp') {
                newIndex = currentIndex > 0 ? currentIndex - 1 : menuItemsArray.length - 1;
            } else {
                newIndex = currentIndex < menuItemsArray.length - 1 ? currentIndex + 1 : 0;
            }

            menuItemsArray[newIndex].click();
            e.preventDefault();
        }
    });

    // Add smooth scroll behavior
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });

    // Initialize tooltips
    const tooltipElements = document.querySelectorAll('[data-tooltip]');
    tooltipElements.forEach(element => {
        element.addEventListener('mouseenter', (e) => {
            const tooltip = document.createElement('div');
            tooltip.className = 'tooltip';
            tooltip.textContent = element.getAttribute('data-tooltip');
            document.body.appendChild(tooltip);

            const rect = element.getBoundingClientRect();
            tooltip.style.cssText = `
                position: fixed;
                top: ${rect.top - tooltip.offsetHeight - 8}px;
                left: ${rect.left + rect.width / 2 - tooltip.offsetWidth / 2}px;
                background: #000;
                color: #fff;
                padding: 8px 12px;
                border-radius: 6px;
                font-size: 12px;
                z-index: 1000;
                pointer-events: none;
                animation: fadeIn 0.2s ease-out;
            `;

            element._tooltip = tooltip;
        });

        element.addEventListener('mouseleave', () => {
            if (element._tooltip) {
                element._tooltip.remove();
                element._tooltip = null;
            }
        });
    });

    // Log initialization
    console.log('🌸 White Blossom Theme - Zalith Launcher Demo Initialized');
    console.log('Version: 1.4.1.2');
    console.log('Theme: White Blossom Edition');
});
