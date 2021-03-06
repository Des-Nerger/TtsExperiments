clear ; close all; clc;

%data = load('samples/android.mandarin.sample2');
%data = load('samples/android.english.sample4');
data = load('samples/android.japanese.sample0');
%x = data(81, 2) ./ data(1:81, 2); y = data(1:81, 1);
x = data(81, 2) ./ data(81:end, 2); y = data(81:end, 1);
m = length(y);

%initial_theta = [5 -4 0];
initial_theta = -5 + 10*rand(1,3);
printf("initial_theta = [%.2f, %.2f, %.2f]\n", initial_theta);

%function b = invX(a)
%	b = [a(1)+a(2)/a(3), -a(2)/a(3)^2, 1/a(3)];
%end

options = optimset('GradObj', 'on', 'MaxIter', 100000);
[thetaUnc, costUnc] = fminunc(@(t)(costFunction(t, x, y)), initial_theta, options);
printf("thetaUnc = [%.7f, %.7f, %.7f];    costUnc = %.7e\n", thetaUnc, costUnc);
%printf("       --> [%.7f, %.7f, %.7f]\n", invX(thetaUnc));

plot(x,y,'.'); hold on; plot(x, hyprb(thetaUnc,x), '-r', 'LineWidth', 2); hold off;

thetaSearch = fminsearch(@(t)(costFunction(t, x, y)(:,1)), initial_theta);
costSearch = costFunction(thetaSearch, x, y)(:,1);
printf("thetaSearch = [%.7f, %.7f, %.7f];    costSearch = %.7e\n", thetaSearch, costSearch);
%printf("          --> [%.7f, %.7f, %.7f]\n", invX(thetaSearch));

hold on; plot(x, hyprb(thetaSearch,x), '--g', 'LineWidth', 2); hold off;

